package com.saasno.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.BranchEntity;
import com.saasno.entity.ProductEntity;
import com.saasno.entity.StockMovementEntity;
import com.saasno.entity.StockOpnameEntity;
import com.saasno.entity.StockOpnameItemEntity;
import com.saasno.repository.ProductRepository;
import com.saasno.repository.StockOpnameRepository;
import com.saasno.repository.StockRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.stock.StockOpnameDTO;
import com.saasno.utility.dto.stock.StockOpnameItemDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

// Stok opname: user menghitung stok fisik di cabangnya sendiri, hasilnya berlaku setelah disetujui orang lain (yang
// punya izin dan cabangnya mencakup). Saldo sistem dicatat saat diajukan; saat disetujui, SELISIH (hitung - sistem)
// diterapkan ke saldo saat itu sebagai mutasi ADJUST, bukan saldo diganti jumlah hitung. Jadi penjualan yang terjadi
// selama penghitungan tidak terhapus. Pengaju tidak boleh menyetujui pengajuannya sendiri.
@Service
public class StockOpnameService {

	private static final String REFERENCE = "STOCK_OPNAME";
	private static final int MAX_ITEMS = 500;
	private static final int MAX_NOTE = 500;

	@Autowired
	private StockOpnameRepository opnameRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StockService stockService;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private NotificationPublisher notificationPublisher;

	@Autowired
	private NotificationRecipients notificationRecipients;

	@Autowired
	private SessionUtility sessionUtility;

	// ---------------------------------------------------------------- ajukan

	@Transactional
	public ResponseModel<StockOpnameDTO> request(StockOpnameDTO request) {
		BranchScope scope = branchScopeService.current();
		BranchEntity branch = branchScopeService.ownBranch(scope, request.getBranchId(),
				"Stok opname hanya dapat diajukan untuk cabang Anda sendiri");
		String note = optionalNote(request.getNote());
		List<StockOpnameItemDTO> requested = request.getItems() == null ? List.of() : request.getItems();
		if (requested.isEmpty()) {
			throw new ApiException("400", "Barang tidak boleh kosong");
		}
		if (requested.size() > MAX_ITEMS) {
			throw new ApiException("400", "Barang tidak boleh lebih dari " + MAX_ITEMS);
		}

		Map<String, BigDecimal> counted = new HashMap<>();
		Map<String, BigDecimal> system = new HashMap<>();
		for (StockOpnameItemDTO item : requested) {
			String productId = item == null ? null : clean(item.getProductId());
			if (productId == null) {
				throw new ApiException("400", "Produk tidak boleh kosong");
			}
			if (counted.containsKey(productId)) {
				throw new ApiException("400", "Produk tidak boleh ganda");
			}
			ProductEntity product = stockService.visibleProduct(branch.getId(), productId);
			// Hasil hitung boleh 0 (stok fisik kosong).
			counted.put(productId, stockService.toBase(product, item.getQuantity(), item.getUnit(), true));
			system.put(productId, stockRepository.quantity(branch.getId(), productId));
		}

		StockOpnameEntity opname = new StockOpnameEntity();
		opname.setBranchId(branch.getId());
		opname.setNote(note);
		opname.setRequestedBy(sessionUtility.getUserId());
		opnameRepository.store(opname);
		// Header harus sudah tertulis sebelum item (item tidak punya relasi JPA ke header).
		opnameRepository.getEntityManager().flush();
		counted.keySet().stream().sorted().forEach(productId -> opnameRepository
				.addItem(new StockOpnameItemEntity(opname.getId(), productId, system.get(productId), counted.get(productId))));
		long differences = counted.keySet().stream().filter(id -> counted.get(id).compareTo(system.get(id)) != 0).count();
		notifyRequested(opname, branch, counted.size(), differences);
		return new ResponseModel<StockOpnameDTO>().success(dtos(List.of(opname)).get(0));
	}

	// Yang berhak menyetujui di cabang itu (selain pengaju) diberi tahu ada pengajuan baru.
	private void notifyRequested(StockOpnameEntity opname, BranchEntity branch, int products, long differences) {
		Set<String> approvers = notificationRecipients.withApi("PUT_API_STOCK_OPNAME_APPROVE", branch.getId());
		approvers.remove(opname.getRequestedBy());
		notificationPublisher.notify(approvers, "STOCK_OPNAME_REQUEST", "Stok opname menunggu persetujuan",
				opname.getSerial() + " di " + branch.getName() + ": " + products + " produk dihitung, " + differences
						+ " berselisih. Diajukan oleh " + notificationRecipients.username(opname.getRequestedBy()) + ".",
				"/stock/opname");
	}

	// Pengaju diberi tahu hasil keputusan.
	private void notifyDecided(StockOpnameEntity opname, boolean approved, String note) {
		String decider = notificationRecipients.username(sessionUtility.getUserId());
		notificationPublisher.notify(List.of(opname.getRequestedBy()),
				approved ? "STOCK_OPNAME_APPROVED" : "STOCK_OPNAME_REJECTED",
				approved ? "Stok opname disetujui" : "Stok opname ditolak",
				opname.getSerial() + (approved ? " disetujui oleh " : " ditolak oleh ") + decider
						+ (note == null ? "." : ". Catatan: " + note),
				"/stock/opname");
	}

	// ---------------------------------------------------------------- baca

	// Stok opname di cabang dalam cakupan user.
	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<StockOpnameDTO>> read(HttpServletRequest request) {
		try {
			return new ResponseEntity<>(find(new DataFilter(request), branchScopeService.current()), HttpStatus.OK);
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<>(new DataResult<StockOpnameDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	// Isi read untuk satu filter dan cakupan cabang (dipakai juga oleh pengunduhan).
	@Transactional(readOnly = true)
	public DataResult<StockOpnameDTO> find(DataFilter requested, BranchScope scope) {
		DataFilter filter = scope.apply(requested, "branchId");
		if (filter.getOrder() == null) {
			filter.setOrder("registered");
			filter.setDescending(true);
		}
		DataResult<StockOpnameEntity> result = opnameRepository.dataTable(filter);
		return 
					new DataResult<>(dtos(result.getData()), result.getTotal(), result.getGrandTotal());
	}

	// ---------------------------------------------------------------- putuskan

	@Transactional
	public ResponseModel<StockOpnameDTO> approve(String id, String decisionNote) {
		StockOpnameEntity opname = lockForDecision(id);
		String note = optionalNote(decisionNote);
		List<StockOpnameItemEntity> items = opnameRepository.findItems(List.of(opname.getId()));
		Map<String, ProductEntity> products = new HashMap<>();
		productRepository.findByIds(items.stream().map(StockOpnameItemEntity::getProductId).collect(Collectors.toSet()))
				.forEach(p -> products.put(p.getId(), p));

		// Urutan produk tetap (findItems sudah terurut) supaya dua persetujuan bersamaan tidak saling mengunci.
		String userId = sessionUtility.getUserId();
		for (StockOpnameItemEntity item : items) {
			BigDecimal delta = item.getDifference();
			if (delta.signum() == 0) {
				continue;
			}
			ProductEntity product = products.get(item.getProductId());
			if (!branchScopeService.subtree(opname.getBranchId()).canSee(product.getBranchId())) {
				throw new ApiException("409", "Produk tidak lagi tersedia di cabang ini: " + product.getSerial() + " "
						+ product.getName());
			}
			if (delta.signum() > 0 && "DISABLED".equals(product.getStatus())) {
				throw new ApiException("409", "Produk nonaktif: " + product.getSerial() + " " + product.getName());
			}
			stockService.applyMovement(opname.getBranchId(), product, delta, StockMovementEntity.ADJUST, REFERENCE,
					opname.getId(), "Stok opname " + opname.getSerial(), userId);
		}

		decide(opname, StockOpnameEntity.APPROVED, userId, note);
		notifyDecided(opname, true, note);
		return new ResponseModel<StockOpnameDTO>().success(dtos(List.of(opname)).get(0));
	}

	@Transactional
	public ResponseModel<StockOpnameDTO> reject(String id, String decisionNote) {
		StockOpnameEntity opname = lockForDecision(id);
		String note = clean(decisionNote);
		if (note == null) {
			throw new ApiException("400", "Alasan tidak boleh kosong");
		}
		if (note.length() > MAX_NOTE) {
			throw new ApiException("400", "Alasan tidak boleh lebih dari " + MAX_NOTE + " karakter");
		}
		decide(opname, StockOpnameEntity.REJECTED, sessionUtility.getUserId(), note);
		notifyDecided(opname, false, note);
		return new ResponseModel<StockOpnameDTO>().success(dtos(List.of(opname)).get(0));
	}

	// Hanya pengaju yang bisa membatalkan, selama masih PENDING.
	@Transactional
	public ResponseModel<StockOpnameDTO> cancel(String id) {
		branchScopeService.current();
		StockOpnameEntity opname = id == null ? null : opnameRepository.findLock(id.trim());
		if (opname == null) {
			throw new ApiException("404", "Stok opname tidak ditemukan");
		}
		String userId = sessionUtility.getUserId();
		if (!opname.getRequestedBy().equals(userId)) {
			throw new ApiException("403", "Hanya pengaju yang dapat membatalkan stok opname ini");
		}
		assertPending(opname);
		decide(opname, StockOpnameEntity.CANCELLED, userId, null);
		return new ResponseModel<StockOpnameDTO>().success(dtos(List.of(opname)).get(0));
	}

	// ---------------------------------------------------------------- bantu

	// Yang boleh memutuskan: user yang cakupannya mencakup cabang opname, bukan pengaju sendiri. Baris dikunci supaya
	// dua keputusan bersamaan untuk opname yang sama tidak keduanya berhasil.
	private StockOpnameEntity lockForDecision(String id) {
		BranchScope scope = branchScopeService.current();
		StockOpnameEntity opname = id == null ? null : opnameRepository.findLock(id.trim());
		if (opname == null || !scope.contains(opname.getBranchId())) {
			throw new ApiException("404", "Stok opname tidak ditemukan");
		}
		if (opname.getRequestedBy().equals(sessionUtility.getUserId())) {
			throw new ApiException("403", "Anda tidak dapat memutuskan stok opname Anda sendiri");
		}
		assertPending(opname);
		return opname;
	}

	private void assertPending(StockOpnameEntity opname) {
		if (!StockOpnameEntity.PENDING.equals(opname.getStatus())) {
			throw new ApiException("409", "Stok opname sudah " + opname.getStatus());
		}
	}

	private void decide(StockOpnameEntity opname, String status, String userId, String note) {
		opname.setStatus(status);
		opname.setDecidedBy(userId);
		opname.setDecidedAt(LocalDateTime.now());
		opname.setDecisionNote(note);
		opnameRepository.update(opname);
	}

	private String optionalNote(String value) {
		String note = clean(value);
		if (note != null && note.length() > MAX_NOTE) {
			throw new ApiException("400", "Catatan tidak boleh lebih dari " + MAX_NOTE + " karakter");
		}
		return note;
	}

	private List<StockOpnameDTO> dtos(List<StockOpnameEntity> opnames) {
		Set<String> ids = opnames.stream().map(StockOpnameEntity::getId).collect(Collectors.toSet());
		Map<String, List<StockOpnameItemEntity>> items = opnameRepository.findItems(ids).stream()
				.collect(Collectors.groupingBy(StockOpnameItemEntity::getOpnameId));
		Set<String> productIds = items.values().stream().flatMap(List::stream).map(StockOpnameItemEntity::getProductId)
				.collect(Collectors.toSet());
		Map<String, ProductEntity> products = new HashMap<>();
		productRepository.findByIds(productIds).forEach(p -> products.put(p.getId(), p));
		Set<String> branchIds = new HashSet<>();
		Set<String> userIds = new HashSet<>();
		opnames.forEach(o -> {
			branchIds.add(o.getBranchId());
			userIds.add(o.getRequestedBy());
			if (o.getDecidedBy() != null) {
				userIds.add(o.getDecidedBy());
			}
		});
		Map<String, String> branchNames = branchScopeService.names(branchIds);
		Map<String, String> usernames = userRepository.findUsernames(userIds);

		return opnames.stream().map(o -> {
			StockOpnameDTO dto = new StockOpnameDTO();
			dto.setId(o.getId());
			dto.setSerial(o.getSerial());
			dto.setBranchId(o.getBranchId());
			dto.setBranchName(branchNames.get(o.getBranchId()));
			dto.setStatus(o.getStatus());
			dto.setNote(o.getNote());
			dto.setRequestedBy(o.getRequestedBy());
			dto.setRequestedByName(usernames.get(o.getRequestedBy()));
			dto.setRegistered(o.getRegistered());
			dto.setDecidedBy(o.getDecidedBy());
			dto.setDecidedByName(o.getDecidedBy() == null ? null : usernames.get(o.getDecidedBy()));
			dto.setDecidedAt(o.getDecidedAt());
			dto.setDecisionNote(o.getDecisionNote());
			dto.setItems(items.getOrDefault(o.getId(), List.of()).stream().map(i -> {
				ProductEntity product = products.get(i.getProductId());
				StockOpnameItemDTO item = new StockOpnameItemDTO();
				item.setProductId(i.getProductId());
				item.setProductSerial(product.getSerial());
				item.setProductName(product.getName());
				item.setUnit(product.getUnit());
				item.setSystemQuantity(i.getSystemQuantity());
				item.setCountedQuantity(i.getCountedQuantity());
				item.setDifference(i.getDifference());
				return item;
			}).collect(Collectors.toList()));
			return dto;
		}).collect(Collectors.toList());
	}

	private String clean(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
