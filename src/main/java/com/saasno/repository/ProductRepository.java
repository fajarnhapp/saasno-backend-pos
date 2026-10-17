package com.saasno.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.ProductEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@Repository
public class ProductRepository extends JpaRepository<ProductEntity, String> {

	private static final DateTimeFormatter SERIAL_PREFIX = DateTimeFormatter.ofPattern("MMyyyydd");
	private static final int SERIAL_DIGITS = 3;

	public ProductRepository() {
		super(ProductEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<ProductEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<ProductEntity>(entityManager);
	}

	public DataResult<ProductEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(ProductEntity.class, dataFilters);
	}

	public List<ProductEntity> findByIds(java.util.Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return entityManager.createQuery("SELECT p FROM ProductEntity p WHERE p.id IN :ids", ProductEntity.class)
				.setParameter("ids", ids).getResultList();
	}

	// Waktu foto katalog terakhir diganti (epoch milidetik, zona JVM) untuk produk-produk ini; produk tanpa foto tidak ada di hasil.
	@SuppressWarnings("unchecked")
	public java.util.Map<String, Long> imageVersions(java.util.Collection<String> ids) {
		java.util.Map<String, Long> versions = new java.util.HashMap<>();
		if (ids == null || ids.isEmpty()) {
			return versions;
		}
		List<Object[]> rows = entityManager.createNativeQuery("SELECT product_id, image_updated FROM public.product_catalog "
				+ "WHERE product_id IN (:ids) AND image_updated IS NOT NULL").setParameter("ids", ids).getResultList();
		for (Object[] r : rows) {
			java.time.LocalDateTime time = r[1] instanceof java.sql.Timestamp t ? t.toLocalDateTime() : (java.time.LocalDateTime) r[1];
			versions.put((String) r[0], time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
		}
		return versions;
	}

	public List<String> findUsedUnits() {
		return entityManager.createQuery("SELECT DISTINCT p.unit FROM ProductEntity p", String.class).getResultList();
	}

	@Override
	@Transactional
	public void store(ProductEntity entity) {
		// Lock dilepas saat commit, jadi insert lain menunggu sampai serial ini tersimpan.
		entityManager.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('product_serial'))) l")
				.getSingleResult();
		entity.setSerial(nextSerial());
		super.store(entity);
	}

	private String nextSerial() {
		String prefix = LocalDate.now().format(SERIAL_PREFIX);
		String latest = entityManager
				.createQuery("SELECT MAX(p.serial) FROM ProductEntity p WHERE p.serial LIKE :prefix", String.class)
				.setParameter("prefix", prefix + "%")
				.getSingleResult();
		int sequence = latest == null ? 1 : Integer.parseInt(latest.substring(prefix.length())) + 1;
		return prefix + String.format("%0" + SERIAL_DIGITS + "d", sequence);
	}

}
