package com.saasno.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.StockTransferEntity;
import com.saasno.entity.StockTransferItemEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@Repository
public class StockTransferRepository extends JpaRepository<StockTransferEntity, String> {

	private static final DateTimeFormatter SERIAL_PREFIX = DateTimeFormatter.ofPattern("MMyyyydd");
	private static final int SERIAL_DIGITS = 3;

	public StockTransferRepository() {
		super(StockTransferEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<StockTransferEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<StockTransferEntity>(entityManager);
	}

	public DataResult<StockTransferEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(StockTransferEntity.class, dataFilters);
	}

	public List<StockTransferItemEntity> findItems(Collection<String> transferIds) {
		if (transferIds == null || transferIds.isEmpty()) {
			return List.of();
		}
		return entityManager.createQuery(
				"SELECT i FROM StockTransferItemEntity i WHERE i.transferId IN :ids ORDER BY i.productId",
				StockTransferItemEntity.class).setParameter("ids", transferIds).getResultList();
	}

	public void addItem(StockTransferItemEntity item) {
		entityManager.persist(item);
	}

	// Jumlah oper stok yang menyangkut cabang ini (sebagai asal atau tujuan).
	public long countByBranch(String branchId) {
		return entityManager.createQuery(
				"SELECT COUNT(t) FROM StockTransferEntity t WHERE t.sourceBranchId = :id OR t.destinationBranchId = :id",
				Long.class).setParameter("id", branchId).getSingleResult();
	}

	@Override
	@Transactional
	public void store(StockTransferEntity entity) {
		// Lock dilepas saat commit, jadi insert lain menunggu sampai serial ini tersimpan.
		entityManager.createNativeQuery(
				"SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('stock_transfer_serial'))) l").getSingleResult();
		entity.setSerial(nextSerial());
		super.store(entity);
	}

	private String nextSerial() {
		String prefix = LocalDate.now().format(SERIAL_PREFIX);
		String latest = entityManager
				.createQuery("SELECT MAX(t.serial) FROM StockTransferEntity t WHERE t.serial LIKE :prefix", String.class)
				.setParameter("prefix", prefix + "%")
				.getSingleResult();
		int sequence = latest == null ? 1 : Integer.parseInt(latest.substring(prefix.length())) + 1;
		return prefix + String.format("%0" + SERIAL_DIGITS + "d", sequence);
	}

}
