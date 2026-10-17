package com.saasno.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.PettyCashEntryEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;

// Kas kecil per cabang. Saldo boleh minus (diberi peringatan, tidak ditolak); setiap perubahan saldo harus diikuti
// satu baris ledger lewat addEntry.
@Repository
public class PettyCashRepository extends JpaRepository<PettyCashEntryEntity, String> {

	public PettyCashRepository() {
		super(PettyCashEntryEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<PettyCashEntryEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<PettyCashEntryEntity>(entityManager);
	}

	public DataResult<PettyCashEntryEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(PettyCashEntryEntity.class, dataFilters);
	}

	public void addEntry(PettyCashEntryEntity entry) {
		entityManager.persist(entry);
	}

	// Mengubah saldo secara atomik (membuat baris kalau belum ada) dan mengembalikan saldo baru. Saldo boleh minus.
	public BigDecimal adjust(String branchId, BigDecimal delta) {
		entityManager.createNativeQuery(
				"INSERT INTO public.petty_cash_balance (branch_id, balance, modificated) VALUES (:branch, :delta, :now) "
						+ "ON CONFLICT (branch_id) DO UPDATE SET balance = public.petty_cash_balance.balance + EXCLUDED.balance, "
						+ "modificated = EXCLUDED.modificated")
				.setParameter("branch", branchId).setParameter("delta", delta).setParameter("now", LocalDateTime.now())
				.executeUpdate();
		return balance(branchId);
	}

	public BigDecimal balance(String branchId) {
		List<?> rows = entityManager.createNativeQuery("SELECT balance FROM public.petty_cash_balance WHERE branch_id = :branch")
				.setParameter("branch", branchId).getResultList();
		return rows.isEmpty() ? BigDecimal.ZERO : (BigDecimal) rows.get(0);
	}

	public Map<String, BigDecimal> balances(Collection<String> branchIds) {
		Map<String, BigDecimal> result = new HashMap<>();
		if (branchIds == null || branchIds.isEmpty()) {
			return result;
		}
		for (Object row : entityManager.createNativeQuery(
				"SELECT branch_id, balance FROM public.petty_cash_balance WHERE branch_id IN (:ids)")
				.setParameter("ids", branchIds).getResultList()) {
			Object[] columns = (Object[]) row;
			result.put((String) columns[0], (BigDecimal) columns[1]);
		}
		return result;
	}

}
