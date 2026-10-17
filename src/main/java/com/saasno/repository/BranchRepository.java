package com.saasno.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.BranchEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@Repository
public class BranchRepository extends JpaRepository<BranchEntity, String> {

	private static final DateTimeFormatter SERIAL_PREFIX = DateTimeFormatter.ofPattern("MMyyyydd");
	private static final int SERIAL_DIGITS = 3;

	public BranchRepository() {
		super(BranchEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<BranchEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<BranchEntity>(entityManager);
	}

	public DataResult<BranchEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(BranchEntity.class, dataFilters);
	}

	public List<BranchEntity> findByIds(Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return entityManager.createQuery("SELECT b FROM BranchEntity b WHERE b.id IN :ids", BranchEntity.class)
				.setParameter("ids", ids)
				.getResultList();
	}

	// Cabang ini beserta seluruh turunannya. UNION (bukan UNION ALL) membuat data siklus tidak
	// menyebabkan loop tak berujung.
	@SuppressWarnings("unchecked")
	public java.util.Set<String> findSubtreeIds(String rootId) {
		List<String> ids = entityManager.createNativeQuery(
				"WITH RECURSIVE tree(id) AS (SELECT id FROM public.branch WHERE id = :rootId "
						+ "UNION SELECT b.id FROM public.branch b JOIN tree t ON b.parent_id = t.id) "
						+ "SELECT id FROM tree")
				.setParameter("rootId", rootId)
				.getResultList();
		return new java.util.HashSet<>(ids);
	}

	// Mengunci perubahan induk cabang sampai transaksi selesai (advisory lock, dilepas otomatis saat commit/rollback).
	// Tanpa ini dua pemindahan berbarengan (A ke bawah B dan B ke bawah A) sama-sama lolos cek siklus.
	public void lockHierarchy() {
		entityManager.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('branch.hierarchy'))) AS locked").getSingleResult();
	}

	public List<BranchEntity> findAllByName() {
		return entityManager.createQuery("SELECT b FROM BranchEntity b ORDER BY b.name, b.serial", BranchEntity.class)
				.getResultList();
	}

	// Cabang ini beserta seluruh induknya sampai ke atas.
	@SuppressWarnings("unchecked")
	public java.util.Set<String> findAncestorIds(String branchId) {
		List<String> ids = entityManager.createNativeQuery(
				"WITH RECURSIVE up(id, parent_id) AS (SELECT id, parent_id FROM public.branch WHERE id = :id "
						+ "UNION SELECT b.id, b.parent_id FROM public.branch b JOIN up u ON b.id = u.parent_id) "
						+ "SELECT id FROM up")
				.setParameter("id", branchId)
				.getResultList();
		return new java.util.HashSet<>(ids);
	}

	// Jumlah data (user, produk, promo, customer, stok, oper stok) yang masih berada di cabang ini.
	public long countOwnedData(String branchId) {
		Number total = (Number) entityManager.createNativeQuery(
				"SELECT (SELECT COUNT(*) FROM public.\"user\" WHERE branch_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.product WHERE branch_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.promo WHERE branch_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.customer WHERE branch_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.sale WHERE branch_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.sale_return WHERE branch_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.sale_hold WHERE branch_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.article WHERE branch_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.store_expense WHERE branch_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.petty_cash_entry WHERE branch_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.petty_cash_balance WHERE branch_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.stock_balance WHERE branch_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.stock_movement WHERE branch_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.stock_opname WHERE branch_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.online_order WHERE branch_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.stock_transfer"
						+ "    WHERE source_branch_id = :id OR destination_branch_id = :id)")
				.setParameter("id", branchId)
				.getSingleResult();
		return total.longValue();
	}

	public long countChildren(String parentId) {
		return entityManager.createQuery("SELECT COUNT(b) FROM BranchEntity b WHERE b.parentId = :parentId", Long.class)
				.setParameter("parentId", parentId)
				.getSingleResult();
	}

	@Override
	@Transactional
	public void store(BranchEntity entity) {
		// Lock dilepas saat commit, jadi insert lain menunggu sampai serial ini tersimpan.
		entityManager.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('branch_serial'))) l")
				.getSingleResult();
		entity.setSerial(nextSerial());
		super.store(entity);
	}

	private String nextSerial() {
		String prefix = LocalDate.now().format(SERIAL_PREFIX);
		String latest = entityManager
				.createQuery("SELECT MAX(b.serial) FROM BranchEntity b WHERE b.serial LIKE :prefix", String.class)
				.setParameter("prefix", prefix + "%")
				.getSingleResult();
		int sequence = latest == null ? 1 : Integer.parseInt(latest.substring(prefix.length())) + 1;
		return prefix + String.format("%0" + SERIAL_DIGITS + "d", sequence);
	}

}
