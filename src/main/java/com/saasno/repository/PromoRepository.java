package com.saasno.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.PromoEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@Repository
public class PromoRepository extends JpaRepository<PromoEntity, String> {

	private static final DateTimeFormatter SERIAL_PREFIX = DateTimeFormatter.ofPattern("MMyyyydd");
	private static final int SERIAL_DIGITS = 3;

	public PromoRepository() {
		super(PromoEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<PromoEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<PromoEntity>(entityManager);
	}

	public DataResult<PromoEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(PromoEntity.class, dataFilters);
	}

	// Promo yang sedang berlaku (aktif dan dalam periode) milik salah satu cabang tersebut.
	public java.util.List<PromoEntity> findActive(java.util.Collection<String> branchIds, LocalDate today) {
		if (branchIds == null || branchIds.isEmpty()) {
			return java.util.List.of();
		}
		return entityManager.createQuery(
				"SELECT p FROM PromoEntity p WHERE p.branchId IN :ids AND p.status = 'ACTIVE' "
						+ "AND (p.validFrom IS NULL OR p.validFrom <= :today) "
						+ "AND (p.validUntil IS NULL OR p.validUntil >= :today) ORDER BY p.serial",
				PromoEntity.class).setParameter("ids", branchIds).setParameter("today", today).getResultList();
	}

	// Jumlah promo yang memakai produk ini sebagai syarat atau hadiah.
	public long countByProduct(String productId) {
		return entityManager.createQuery(
				"SELECT COUNT(p) FROM PromoEntity p WHERE p.productId = :id OR p.rewardProductId = :id", Long.class)
				.setParameter("id", productId).getSingleResult();
	}

	// Jumlah promo yang menargetkan customer ini secara langsung.
	public long countByCustomer(String customerId) {
		return entityManager.createQuery(
				"SELECT COUNT(p) FROM PromoEntity p JOIN p.customers c WHERE c.id = :id", Long.class)
				.setParameter("id", customerId).getSingleResult();
	}

	@Override
	@Transactional
	public void store(PromoEntity entity) {
		// Lock dilepas saat commit, jadi insert lain menunggu sampai serial ini tersimpan.
		entityManager.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('promo_serial'))) l")
				.getSingleResult();
		entity.setSerial(nextSerial());
		super.store(entity);
	}

	private String nextSerial() {
		String prefix = LocalDate.now().format(SERIAL_PREFIX);
		String latest = entityManager
				.createQuery("SELECT MAX(p.serial) FROM PromoEntity p WHERE p.serial LIKE :prefix", String.class)
				.setParameter("prefix", prefix + "%")
				.getSingleResult();
		int sequence = latest == null ? 1 : Integer.parseInt(latest.substring(prefix.length())) + 1;
		return prefix + String.format("%0" + SERIAL_DIGITS + "d", sequence);
	}

}
