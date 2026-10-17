package com.saasno.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.SaleEntity;
import com.saasno.entity.SaleItemEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;

@Repository
public class SaleRepository extends JpaRepository<SaleEntity, String> {

	private static final DateTimeFormatter SERIAL_DAY = DateTimeFormatter.ofPattern("yyMMdd");
	private static final int SERIAL_DIGITS = 4;

	public SaleRepository() {
		super(SaleEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<SaleEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<SaleEntity>(entityManager);
	}

	public DataResult<SaleEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(SaleEntity.class, dataFilters);
	}

	public List<SaleItemEntity> findItems(Collection<String> saleIds) {
		if (saleIds == null || saleIds.isEmpty()) {
			return List.of();
		}
		return entityManager.createQuery(
				"SELECT i FROM SaleItemEntity i WHERE i.saleId IN :ids ORDER BY i.free, i.productName, i.id",
				SaleItemEntity.class).setParameter("ids", saleIds).getResultList();
	}

	public SaleItemEntity findItem(String itemId) {
		return itemId == null ? null : entityManager.find(SaleItemEntity.class, itemId);
	}

	public List<SaleItemEntity> findItemsByIds(Collection<String> itemIds) {
		if (itemIds == null || itemIds.isEmpty()) {
			return List.of();
		}
		return entityManager.createQuery("SELECT i FROM SaleItemEntity i WHERE i.id IN :ids", SaleItemEntity.class)
				.setParameter("ids", itemIds).getResultList();
	}

	public void addItem(SaleItemEntity item) {
		entityManager.persist(item);
	}

	public SaleEntity findByRequestKey(String userId, String requestKey) {
		List<SaleEntity> found = entityManager.createQuery(
				"SELECT s FROM SaleEntity s WHERE s.createdBy = :user AND s.requestKey = :key", SaleEntity.class)
				.setParameter("user", userId).setParameter("key", requestKey).getResultList();
		return found.isEmpty() ? null : found.get(0);
	}

	public long countByCustomer(String customerId) {
		return entityManager.createQuery("SELECT COUNT(s) FROM SaleEntity s WHERE s.customerId = :id", Long.class)
				.setParameter("id", customerId).getSingleResult();
	}

	// Nomor struk: <serial cabang>/<yyMMdd>/<urutan harian 4 digit>, urutan per cabang per hari.
	public void store(SaleEntity entity, String branchSerial) {
		String prefix = branchSerial + "/" + LocalDate.now().format(SERIAL_DAY) + "/";
		// Lock dilepas saat commit, jadi insert lain untuk cabang dan hari yang sama menunggu.
		entityManager.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:key))) l")
				.setParameter("key", "sale_serial:" + prefix).getSingleResult();
		String latest = entityManager
				.createQuery("SELECT MAX(s.serial) FROM SaleEntity s WHERE s.serial LIKE :prefix", String.class)
				.setParameter("prefix", prefix + "%").getSingleResult();
		int sequence = latest == null ? 1 : Integer.parseInt(latest.substring(prefix.length())) + 1;
		entity.setSerial(prefix + String.format("%0" + SERIAL_DIGITS + "d", sequence));
		entityManager.persist(entity);
	}

}
