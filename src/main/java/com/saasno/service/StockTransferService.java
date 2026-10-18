package com.saasno.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.saasno.entity.StockTransferEntity;
import com.saasno.entity.StockTransferItemEntity;
import com.saasno.repository.BranchRepository;
import com.saasno.repository.ProductRepository;
import com.saasno.repository.StockRepository;
import com.saasno.repository.StockTransferRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.branch.BranchDTO;
import com.saasno.utility.dto.stock.StockTransferDTO;
import com.saasno.utility.dto.stock.StockTransferItemDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

// Oper stok: user di cabang asal (cabangnya sendiri) mengajukan, cabang tujuan menyetujui. Stok baru berpindah saat disetujui,
// dalam satu transaksi (asal berkurang, tujuan bertambah, dua baris mutasi). Pengaju tidak boleh
// menyetujui pengajuannya sendiri.
@Service
public class StockTransferService {

	@Autowired
	private BatchService batchService;

	private static final String REFERENCE = "STOCK_TRANSFER";
	private static final int MAX_ITEMS = 100;
	private static final int MAX_NOTE = 500;

	@Autowired
	private StockTransferRepository transferRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private BranchRepository branchRepository;

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
	public ResponseModel<StockTransferDTO> request(StockTransferDTO request) {
		BranchScope scope = branchScopeService.current();
		String source = clean(request.getSourceBranchId());
		String destination = clean(request.getDestinationBranchId());
		// Asal selalu cabang tempat pengaju berada (tidak dipilih); hanya user global, yang tidak punya
		// cabang, harus menyebutkannya. Tujuan boleh cabang mana pun (atas, bawah, atau saudara).
		if (!scope.isGlobal()) {
			if (source != null && !source.equals(scope.getBranchId())) {
				throw new ApiException("403", "Stok hanya dapat dioper dari cabang Anda sendiri");
			}
			source = scope.getBranchId();
		}
		if (source == null) {
			throw new ApiException("400", "Cabang asal tidak boleh kosong");
		}
		if (branchRepository.find(source) == null) {
			throw new ApiException("404", "Cabang asal tidak ditemukan");
		}
		if (destination == null || branchRepository.find(destination) == null) {
			throw new ApiException("404", "Cabang tujuan tidak ditemukan");
		}
		if (source.equals(destination)) {
			throw new ApiException("400", "Cabang asal dan tujuan harus berbeda");
		}
		assertHierarchy(source, destination);
		String note = clean(request.getNote());
		if (note != null && note.length() > MAX_NOTE) {
			throw new ApiException("400", "Catatan tidak boleh lebih dari " + MAX_NOTE + " karakter");
		}
		List<StockTransferItemDTO> requested = request.getItems() == null ? List.of() : request.getItems();
		if (requested.isEmpty()) {
			throw new ApiException("400", "Barang tidak boleh kosong");
		}
		if (requested.size() > MAX_ITEMS) {
			throw new ApiException("400", "Barang tidak boleh lebih dari " + MAX_ITEMS);
		}

		Map<String, BigDecimal> quantities = new HashMap<>();
		for (StockTransferItemDTO item : requested) {
			String productId = item == null ? null : clean(item.getProductId());
			if (productId == null) {
				throw new ApiException("400", "Produk tidak boleh kosong");
			}
			if (quantities.containsKey(productId)) {
				throw new ApiException("400", "Produk tidak boleh ganda");
			}
			ProductEntity product = stockService.visibleProduct(source, productId);
			assertAvailableAtDestination(product, destination);
			stockService.assertActive(product);
			BigDecimal quantity = stockService.toBase(product, item.getQuantity(), item.getUnit(), false);
			// Pengecekan awal; yang menentukan adalah pengecekan saat disetujui.
			if (stockRepository.quantity(source, productId).compareTo(quantity) < 0) {
				throw new ApiException("409", "Stok tidak cukup untuk " + product.getSerial() + " " + product.getName());
			}
			quantities.put(productId, quantity);
		}

		StockTransferEntity transfer = new StockTransferEntity();
		transfer.setSourceBranchId(source);
		transfer.setDestinationBranchId(destination);
		transfer.setNote(note);
		transfer.setRequestedBy(sessionUtility.getUserId());
		transferRepository.store(transfer);
		// Header harus sudah tertulis sebelum item (item tidak punya relasi JPA ke header, jadi urutan
		// insert tidak dijaga Hibernate).
		transferRepository.getEntityManager().flush();
		quantities.entrySet().stream().sorted(Map.Entry.comparingByKey())
				.forEach(e -> transferRepository.addItem(new StockTransferItemEntity(transfer.getId(), e.getKey(), e.getValue())));
		notifyRequested(transfer, quantities.size());
		return new ResponseModel<StockTransferDTO>().success(dtos(List.of(transfer)).get(0));
	}

	// Yang berhak menyetujui di cabang tujuan (selain pengaju) diberi tahu ada permintaan baru.
	private void notifyRequested(StockTransferEntity transfer, int kinds) {
		java.util.Set<String> approvers = notificationRecipients.withApi("PUT_API_STOCK_TRANSFER_APPROVE",
				transfer.getDestinationBranchId());
		approvers.remove(transfer.getRequestedBy());
		Map<String, String> names = branchScopeService.names(List.of(transfer.getSourceBranchId(), transfer.getDestinationBranchId()));
		notificationPublisher.notify(approvers, "STOCK_TRANSFER_REQUEST", "Oper stok menunggu persetujuan",
				transfer.getSerial() + " dari " + names.get(transfer.getSourceBranchId()) + " ke "
						+ names.get(transfer.getDestinationBranchId()) + " (" + kinds + " jenis barang), diajukan oleh "
						+ notificationRecipients.username(transfer.getRequestedBy()) + ".",
				"/stock/transfer");
	}

	// Pengaju diberi tahu hasil keputusan.
	private void notifyDecided(StockTransferEntity transfer, boolean approved, String note) {
		String decider = notificationRecipients.username(sessionUtility.getUserId());
		notificationPublisher.notify(List.of(transfer.getRequestedBy()),
				approved ? "STOCK_TRANSFER_APPROVED" : "STOCK_TRANSFER_REJECTED",
				approved ? "Oper stok disetujui" : "Oper stok ditolak",
				transfer.getSerial() + (approved ? " disetujui oleh " : " ditolak oleh ") + decider
						+ (note == null ? "." : ". Catatan: " + note),
				"/stock/transfer");
	}

	// Tujuan oper stok yang diizinkan dari cabang asal: hanya tetangga langsungnya (lihat assertHierarchy).
	// Hanya id, serial, dan nama; tidak memberi akses ke data cabang lain. Asal user cabang selalu
	// cabangnya sendiri; user global menyebutkan asal (tanpa asal, daftar kosong).
	@Transactional(readOnly = true)
	public ResponseModel<List<BranchDTO>> destinations(String sourceBranchId) {
		BranchScope scope = branchScopeService.current();
		String sourceId = scope.isGlobal() ? clean(sourceBranchId) : scope.getBranchId();
		BranchEntity source = sourceId == null ? null : branchRepository.find(sourceId);
		if (source == null) {
			return new ResponseModel<List<BranchDTO>>().success(List.of());
		}
		List<BranchDTO> list = branchRepository.findAllByName().stream()
				.filter(b -> isNeighbor(source, b))
				.map(b -> {
					BranchDTO dto = new BranchDTO();
					dto.setId(b.getId());
					dto.setSerial(b.getSerial());
					dto.setName(b.getName());
					return dto;
				}).collect(Collectors.toList());
		return new ResponseModel<List<BranchDTO>>().success(list);
	}

	// Oper stok hanya antar tetangga langsung, supaya tidak melompati tingkat: ke induknya, ke anak
	// langsungnya, atau ke saudaranya (satu induk yang sama). A -> B -> C: dari A harus ke B dulu; dari C
	// harus ke B dulu. Cabang tanpa induk tidak punya saudara.
	private boolean isNeighbor(BranchEntity source, BranchEntity destination) {
		if (source.getId().equals(destination.getId())) {
			return false;
		}
		return source.getId().equals(destination.getParentId())
				|| destination.getId().equals(source.getParentId())
				|| (source.getParentId() != null && source.getParentId().equals(destination.getParentId()));
	}

	private void assertHierarchy(String source, String destination) {
		BranchEntity sourceBranch = branchRepository.find(source);
		BranchEntity destinationBranch = branchRepository.find(destination);
		if (sourceBranch == null || destinationBranch == null || !isNeighbor(sourceBranch, destinationBranch)) {
			throw new ApiException("400",
					"Stok hanya dapat dioper ke induk, anak langsung, atau cabang saudara, tidak boleh melompati tingkat");
		}
	}

	// ---------------------------------------------------------------- baca

	// Oper stok yang asal atau tujuannya dalam cakupan user.
	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<StockTransferDTO>> read(HttpServletRequest request) {
		try {
			return new ResponseEntity<>(find(new DataFilter(request), branchScopeService.current()), HttpStatus.OK);
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<>(new DataResult<StockTransferDTO>(BigInteger.ZERO, BigInteger.ZERO),
					HttpStatus.OK);
		}
	}

	// Isi read untuk satu filter dan cakupan cabang (dipakai juga oleh pengunduhan).
	@Transactional(readOnly = true)
	public DataResult<StockTransferDTO> find(DataFilter filter, BranchScope scope) {
		if (!scope.isGlobal()) {
			filter.restrictAny(List.of("sourceBranchId", "destinationBranchId"), scope.getIds());
		}
		if (filter.getOrder() == null) {
			filter.setOrder("registered");
			filter.setDescending(true);
		}
		DataResult<StockTransferEntity> result = transferRepository.dataTable(filter);
		return new DataResult<>(dtos(result.getData()), result.getTotal(), result.getGrandTotal());
	}

	// ---------------------------------------------------------------- putuskan

	@Transactional
	public ResponseModel<StockTransferDTO> approve(String id, String decisionNote) {
		StockTransferEntity transfer = lockForDecision(id);
		String note = optionalNote(decisionNote);

		try {
			assertHierarchy(transfer.getSourceBranchId(), transfer.getDestinationBranchId());
		} catch (ApiException e) {
			throw new ApiException("409", "Oper stok tidak lagi diizinkan oleh hierarki cabang");
		}
		List<StockTransferItemEntity> items = transferRepository.findItems(List.of(transfer.getId()));
		Map<String, ProductEntity> products = new HashMap<>();
		productRepository.findByIds(items.stream().map(StockTransferItemEntity::getProductId).collect(Collectors.toSet()))
				.forEach(p -> products.put(p.getId(), p));

		// Perubahan diterapkan dalam urutan tetap (cabang lalu produk) supaya dua persetujuan bersamaan
		// yang menyentuh saldo yang sama tidak saling mengunci (deadlock).
		record Operation(String branchId, ProductEntity product, BigDecimal delta, String type) {
		}
		List<Operation> operations = new ArrayList<>();
		for (StockTransferItemEntity item : items) {
			ProductEntity product = products.get(item.getProductId());
			if ("DISABLED".equals(product.getStatus())) {
				throw new ApiException("409", "Produk nonaktif: " + product.getSerial() + " " + product.getName());
			}
			if (!branchScopeService.subtree(transfer.getSourceBranchId()).canSee(product.getBranchId())
					|| !branchScopeService.subtree(transfer.getDestinationBranchId()).canSee(product.getBranchId())) {
				throw new ApiException("409", "Produk tidak lagi tersedia di kedua cabang: " + product.getSerial()
						+ " " + product.getName());
			}
			operations.add(new Operation(transfer.getSourceBranchId(), product, item.getQuantity().negate(),
					StockMovementEntity.TRANSFER_OUT));
			operations.add(new Operation(transfer.getDestinationBranchId(), product, item.getQuantity(),
					StockMovementEntity.TRANSFER_IN));
		}
		// Produk berkedaluwarsa: batch dipindah apa adanya (FEFO, tanpa yang sudah kedaluwarsa) sebelum saldo diubah.
		for (StockTransferItemEntity item : items) {
			batchService.transfer(transfer.getSourceBranchId(), transfer.getDestinationBranchId(), products.get(item.getProductId()), item.getQuantity());
		}
		operations.sort(Comparator.comparing(Operation::branchId).thenComparing(o -> o.product().getId()));
		String userId = sessionUtility.getUserId();
		for (Operation op : operations) {
			stockService.applyMovement(op.branchId(), op.product(), op.delta(), op.type(), REFERENCE, transfer.getId(),
					"Oper stok " + transfer.getSerial(), userId);
		}

		decide(transfer, StockTransferEntity.APPROVED, userId, note);
		notifyDecided(transfer, true, note);
		return new ResponseModel<StockTransferDTO>().success(dtos(List.of(transfer)).get(0));
	}

	@Transactional
	public ResponseModel<StockTransferDTO> reject(String id, String decisionNote) {
		StockTransferEntity transfer = lockForDecision(id);
		String note = clean(decisionNote);
		if (note == null) {
			throw new ApiException("400", "Alasan tidak boleh kosong");
		}
		if (note.length() > MAX_NOTE) {
			throw new ApiException("400", "Alasan tidak boleh lebih dari " + MAX_NOTE + " karakter");
		}
		decide(transfer, StockTransferEntity.REJECTED, sessionUtility.getUserId(), note);
		notifyDecided(transfer, false, note);
		return new ResponseModel<StockTransferDTO>().success(dtos(List.of(transfer)).get(0));
	}

	// Hanya pengaju yang bisa membatalkan, selama masih PENDING.
	@Transactional
	public ResponseModel<StockTransferDTO> cancel(String id) {
		branchScopeService.current();
		StockTransferEntity transfer = id == null ? null : transferRepository.findLock(id.trim());
		if (transfer == null) {
			throw new ApiException("404", "Oper stok tidak ditemukan");
		}
		String userId = sessionUtility.getUserId();
		if (!transfer.getRequestedBy().equals(userId)) {
			throw new ApiException("403", "Hanya pengaju yang dapat membatalkan oper stok ini");
		}
		assertPending(transfer);
		decide(transfer, StockTransferEntity.CANCELLED, userId, null);
		return new ResponseModel<StockTransferDTO>().success(dtos(List.of(transfer)).get(0));
	}

	// ---------------------------------------------------------------- bantu

	// Yang boleh memutuskan: user yang cabang tujuannya dalam cakupannya, bukan pengaju sendiri. Baris
	// dikunci supaya dua keputusan bersamaan untuk oper yang sama tidak keduanya berhasil.
	private StockTransferEntity lockForDecision(String id) {
		BranchScope scope = branchScopeService.current();
		StockTransferEntity transfer = id == null ? null : transferRepository.findLock(id.trim());
		if (transfer == null || !scope.contains(transfer.getDestinationBranchId())) {
			throw new ApiException("404", "Oper stok tidak ditemukan");
		}
		if (transfer.getRequestedBy().equals(sessionUtility.getUserId())) {
			throw new ApiException("403", "Anda tidak dapat memutuskan oper stok Anda sendiri");
		}
		assertPending(transfer);
		return transfer;
	}

	private void assertPending(StockTransferEntity transfer) {
		if (!StockTransferEntity.PENDING.equals(transfer.getStatus())) {
			throw new ApiException("409", "Oper stok sudah " + transfer.getStatus());
		}
	}

	private void decide(StockTransferEntity transfer, String status, String userId, String note) {
		transfer.setStatus(status);
		transfer.setDecidedBy(userId);
		transfer.setDecidedAt(LocalDateTime.now());
		transfer.setDecisionNote(note);
		transferRepository.update(transfer);
	}

	// Produk harus terlihat oleh cabang tujuan juga; kalau tidak, cabang tujuan tidak bisa memegang stoknya.
	private void assertAvailableAtDestination(ProductEntity product, String destination) {
		if (!branchScopeService.subtree(destination).canSee(product.getBranchId())) {
			throw new ApiException("400", "Produk tidak tersedia di cabang tujuan: " + product.getSerial()
					+ " " + product.getName());
		}
	}

	private String optionalNote(String value) {
		String note = clean(value);
		if (note != null && note.length() > MAX_NOTE) {
			throw new ApiException("400", "Catatan tidak boleh lebih dari " + MAX_NOTE + " karakter");
		}
		return note;
	}

	private List<StockTransferDTO> dtos(List<StockTransferEntity> transfers) {
		Set<String> ids = transfers.stream().map(StockTransferEntity::getId).collect(Collectors.toSet());
		Map<String, List<StockTransferItemEntity>> items = transferRepository.findItems(ids).stream()
				.collect(Collectors.groupingBy(StockTransferItemEntity::getTransferId));
		Set<String> productIds = items.values().stream().flatMap(List::stream).map(StockTransferItemEntity::getProductId)
				.collect(Collectors.toSet());
		Map<String, ProductEntity> products = new HashMap<>();
		productRepository.findByIds(productIds).forEach(p -> products.put(p.getId(), p));
		Set<String> branchIds = new HashSet<>();
		Set<String> userIds = new HashSet<>();
		transfers.forEach(t -> {
			branchIds.add(t.getSourceBranchId());
			branchIds.add(t.getDestinationBranchId());
			userIds.add(t.getRequestedBy());
			if (t.getDecidedBy() != null) {
				userIds.add(t.getDecidedBy());
			}
		});
		Map<String, String> branchNames = branchScopeService.names(branchIds);
		Map<String, String> usernames = userRepository.findUsernames(userIds);

		return transfers.stream().map(t -> {
			StockTransferDTO dto = new StockTransferDTO();
			dto.setId(t.getId());
			dto.setSerial(t.getSerial());
			dto.setSourceBranchId(t.getSourceBranchId());
			dto.setSourceBranchName(branchNames.get(t.getSourceBranchId()));
			dto.setDestinationBranchId(t.getDestinationBranchId());
			dto.setDestinationBranchName(branchNames.get(t.getDestinationBranchId()));
			dto.setStatus(t.getStatus());
			dto.setNote(t.getNote());
			dto.setRequestedBy(t.getRequestedBy());
			dto.setRequestedByName(usernames.get(t.getRequestedBy()));
			dto.setRegistered(t.getRegistered());
			dto.setDecidedBy(t.getDecidedBy());
			dto.setDecidedByName(t.getDecidedBy() == null ? null : usernames.get(t.getDecidedBy()));
			dto.setDecidedAt(t.getDecidedAt());
			dto.setDecisionNote(t.getDecisionNote());
			dto.setItems(items.getOrDefault(t.getId(), List.of()).stream().map(i -> {
				ProductEntity product = products.get(i.getProductId());
				StockTransferItemDTO item = new StockTransferItemDTO();
				item.setProductId(i.getProductId());
				item.setProductSerial(product.getSerial());
				item.setProductName(product.getName());
				item.setQuantity(i.getQuantity());
				item.setUnit(product.getUnit());
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
