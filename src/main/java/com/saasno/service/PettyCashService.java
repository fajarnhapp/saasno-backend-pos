package com.saasno.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
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
import com.saasno.entity.PettyCashEntryEntity;
import com.saasno.entity.StoreExpenseEntity;
import com.saasno.repository.BranchRepository;
import com.saasno.repository.ExpenseRepository;
import com.saasno.repository.PettyCashRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.finance.PettyCashBalanceDTO;
import com.saasno.utility.dto.finance.PettyCashEntryDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

// Kas kecil per cabang: dana tetap yang dipakai membayar biaya operasional, diisi lewat catatan "isi kas kecil"
// (dari modal, bank, atau setoran pemilik; sumbernya wajib dicatat). Saldo boleh minus dengan peringatan. Ledger tidak
// bisa diubah atau dihapus.
@Service
public class PettyCashService {

	private static final int MAX_NOTE = 500;
	private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");

	@Autowired
	private PettyCashRepository pettyCashRepository;

	@Autowired
	private ExpenseRepository expenseRepository;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private SessionUtility sessionUtility;

	@Transactional
	public ResponseModel<PettyCashEntryDTO> topUp(PettyCashEntryDTO request) {
		BranchScope scope = branchScopeService.current();
		BranchEntity branch = branchScopeService.ownBranch(scope, request.getBranchId(), "Kas kecil hanya dapat diisi di cabang Anda sendiri");
		BigDecimal amount = request.getAmount();
		if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0
				|| amount.setScale(2, RoundingMode.UNNECESSARY).compareTo(MAX_AMOUNT) > 0) {
			throw new ApiException("400", "Nominal tidak valid");
		}
		String note = request.getNote() == null ? null : request.getNote().trim();
		if (note == null || note.isEmpty()) {
			throw new ApiException("400", "Catatan tidak boleh kosong (sebutkan sumber dananya)");
		}
		if (note.length() > MAX_NOTE) {
			throw new ApiException("400", "Catatan tidak boleh lebih dari " + MAX_NOTE + " karakter");
		}
		amount = amount.setScale(2, RoundingMode.UNNECESSARY);
		BigDecimal balance = pettyCashRepository.adjust(branch.getId(), amount);
		PettyCashEntryEntity entry = new PettyCashEntryEntity();
		entry.setBranchId(branch.getId());
		entry.setType(PettyCashEntryEntity.TOPUP);
		entry.setAmount(amount);
		entry.setBalanceAfter(balance);
		entry.setNote(note);
		entry.setCreatedBy(sessionUtility.getUserId());
		pettyCashRepository.addEntry(entry);
		return new ResponseModel<PettyCashEntryDTO>().success(dtos(List.of(entry)).get(0));
	}

	// Riwayat kas kecil di cabang user dan turunannya, yang terbaru dulu.
	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<PettyCashEntryDTO>> read(HttpServletRequest request) {
		try {
			return new ResponseEntity<>(find(new DataFilter(request), branchScopeService.current()), HttpStatus.OK);
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<>(new DataResult<PettyCashEntryDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	// Isi read untuk satu filter dan cakupan cabang (dipakai juga oleh pengunduhan).
	@Transactional(readOnly = true)
	public DataResult<PettyCashEntryDTO> find(DataFilter requested, BranchScope scope) {
		DataFilter filter = scope.apply(requested, "branchId");
		if (filter.getOrder() == null) {
			filter.setOrder("registered");
			filter.setDescending(true);
		}
		DataResult<PettyCashEntryEntity> result = pettyCashRepository.dataTable(filter);
		return new DataResult<>(dtos(result.getData()), result.getTotal(), result.getGrandTotal());
	}

	// Saldo kas kecil tiap cabang dalam cakupan user (cabang yang belum pernah dipakai bersaldo 0).
	@Transactional(readOnly = true)
	public ResponseModel<List<PettyCashBalanceDTO>> balances() {
		BranchScope scope = branchScopeService.current();
		List<BranchEntity> branches = branchRepository.findAllByName().stream()
				.filter(b -> scope.contains(b.getId())).collect(Collectors.toList());
		Map<String, BigDecimal> balances = pettyCashRepository
				.balances(branches.stream().map(BranchEntity::getId).collect(Collectors.toSet()));
		List<PettyCashBalanceDTO> list = new ArrayList<>();
		for (BranchEntity branch : branches) {
			PettyCashBalanceDTO dto = new PettyCashBalanceDTO();
			dto.setBranchId(branch.getId());
			dto.setBranchName(branch.getName());
			dto.setBalance(balances.getOrDefault(branch.getId(), BigDecimal.ZERO));
			list.add(dto);
		}
		return new ResponseModel<List<PettyCashBalanceDTO>>().success(list);
	}

	private List<PettyCashEntryDTO> dtos(List<PettyCashEntryEntity> entries) {
		Map<String, String> branchNames = branchScopeService
				.names(entries.stream().map(PettyCashEntryEntity::getBranchId).collect(Collectors.toSet()));
		Map<String, String> usernames = userRepository
				.findUsernames(entries.stream().map(PettyCashEntryEntity::getCreatedBy).collect(Collectors.toSet()));
		Map<String, String> serials = new HashMap<>();
		Set<String> expenseIds = entries.stream().map(PettyCashEntryEntity::getExpenseId).filter(e -> e != null)
				.collect(Collectors.toSet());
		for (String id : expenseIds) {
			StoreExpenseEntity expense = expenseRepository.find(id);
			serials.put(id, expense == null ? null : expense.getSerial());
		}
		return entries.stream().map(e -> {
			PettyCashEntryDTO dto = new PettyCashEntryDTO();
			dto.setId(e.getId());
			dto.setBranchId(e.getBranchId());
			dto.setBranchName(branchNames.get(e.getBranchId()));
			dto.setType(e.getType());
			dto.setAmount(e.getAmount());
			dto.setBalanceAfter(e.getBalanceAfter());
			dto.setExpenseId(e.getExpenseId());
			dto.setExpenseSerial(e.getExpenseId() == null ? null : serials.get(e.getExpenseId()));
			dto.setNote(e.getNote());
			dto.setCreatedBy(e.getCreatedBy());
			dto.setCreatedByName(usernames.get(e.getCreatedBy()));
			dto.setRegistered(e.getRegistered());
			return dto;
		}).collect(Collectors.toList());
	}

}
