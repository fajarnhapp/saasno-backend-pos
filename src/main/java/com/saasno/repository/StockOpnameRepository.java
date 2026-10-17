package com.saasno.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.StockOpnameEntity;
import com.saasno.entity.StockOpnameItemEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@Repository
public class StockOpnameRepository extends JpaRepository<StockOpnameEntity, String> {

	private static final DateTimeFormatter SERIAL_PREFIX = DateTimeFormatter.ofPattern("MMyyyydd");
	private static final int SERIAL_DIGITS = 3;

	public StockOpnameRepository() {
		super(StockOpnameEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<StockOpnameEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<StockOpnameEntity>(entityManager);
	}

	public DataResult<StockOpnameEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(StockOpnameEntity.class, dataFilters);
	}

	public List<StockOpnameItemEntity> findItems(Collection<String> opnameIds) {
		if (opnameIds == null || opnameIds.isEmpty()) {
			return List.of();
		}
		return entityManager.createQuery(
				"SELECT i FROM StockOpnameItemEntity i WHERE i.opnameId IN :ids ORDER BY i.productId",
				StockOpnameItemEntity.class).setParameter("ids", opnameIds).getResultList();
	}

	public void addItem(StockOpnameItemEntity item) {
		entityManager.persist(item);
	}

	// Jumlah stok opname di cabang ini.
	public long countByBranch(String branchId) {
		return entityManager.createQuery("SELECT COUNT(t) FROM StockOpnameEntity t WHERE t.branchId = :id", Long.class)
				.setParameter("id", branchId).getSingleResult();
	}

	@Override
	@Transactional
	public void store(StockOpnameEntity entity) {
		// Lock dilepas saat commit, jadi insert lain menunggu sampai serial ini tersimpan.
		entityManager.createNativeQuery(
				"SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('stock_opname_serial'))) l").getSingleResult();
		entity.setSerial(nextSerial());
		super.store(entity);
	}

	private String nextSerial() {
		String prefix = LocalDate.now().format(SERIAL_PREFIX);
		String latest = entityManager
				.createQuery("SELECT MAX(t.serial) FROM StockOpnameEntity t WHERE t.serial LIKE :prefix", String.class)
				.setParameter("prefix", prefix + "%")
				.getSingleResult();
		int sequence = latest == null ? 1 : Integer.parseInt(latest.substring(prefix.length())) + 1;
		return prefix + String.format("%0" + SERIAL_DIGITS + "d", sequence);
	}

}
