package com.saasno.service;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.utility.BranchScope;
import com.saasno.utility.dto.batch.BatchDTO;
import com.saasno.utility.dto.finance.ExpenseDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;

// Membuang stok batch yang kedaluwarsa/rusak. Dicatat sebagai pemakaian stok sendiri (STOCK_USE) sehingga nilainya (jumlah x harga
// pokok) langsung masuk biaya di Laporan Keuangan, dan stok hanya diambil dari batch yang dipilih.
@Service
public class BatchDisposalService {

	@Autowired
	private BatchService batchService;

	@Autowired
	private ExpenseService expenseService;

	@Autowired
	private BranchScopeService branchScopeService;

	@Transactional
	public ResponseModel<ExpenseDTO> dispose(String id, BigDecimal quantity, String reason) {
		BranchScope scope = branchScopeService.current();
		BatchDTO batch = batchService.one(id, scope);
		BigDecimal amount = quantity == null ? batch.quantity() : quantity;
		if (amount.signum() <= 0 || amount.compareTo(batch.quantity()) > 0) {
			throw new ApiException("400", "Jumlah harus lebih dari 0 dan tidak melebihi isi batch");
		}
		if (reason == null || reason.isBlank()) {
			throw new ApiException("400", "Alasan tidak boleh kosong");
		}
		ExpenseDTO request = new ExpenseDTO();
		request.setBranchId(batch.branchId());
		request.setProductId(batch.productId());
		request.setQuantity(amount);
		request.setCategory(expenseService.categories().stream().map(ExpenseService.Category::value).filter("OTHER"::equals).findFirst()
				.orElseGet(() -> expenseService.categories().get(0).value()));
		request.setDescription("Pembuangan batch " + (batch.batchNo() == null ? "" : batch.batchNo() + " ") + "kedaluwarsa " + batch.expiryDate());
		request.setReason(reason.trim());
		Object[] result = new Object[1];
		batchService.withForcedBatch(batch.id(), () -> result[0] = expenseService.useStock(request));
		@SuppressWarnings("unchecked")
		ResponseModel<ExpenseDTO> response = (ResponseModel<ExpenseDTO>) result[0];
		return response;
	}
}
