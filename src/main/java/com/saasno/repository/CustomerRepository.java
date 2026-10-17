package com.saasno.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.CustomerEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@Repository
public class CustomerRepository extends JpaRepository<CustomerEntity, String> {

	private static final DateTimeFormatter SERIAL_PREFIX = DateTimeFormatter.ofPattern("MMyyyydd");
	private static final int SERIAL_DIGITS = 3;

	public CustomerRepository() {
		super(CustomerEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<CustomerEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<CustomerEntity>(entityManager);
	}

	public DataResult<CustomerEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(CustomerEntity.class, dataFilters);
	}

	public java.util.List<CustomerEntity> findByIds(java.util.Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return java.util.List.of();
		}
		return entityManager.createQuery("SELECT c FROM CustomerEntity c WHERE c.id IN :ids", CustomerEntity.class)
				.setParameter("ids", ids).getResultList();
	}

	@Override
	@Transactional
	public void store(CustomerEntity entity) {
		// Lock dilepas saat commit, jadi insert lain menunggu sampai serial ini tersimpan.
		entityManager.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('customer_serial'))) l")
				.getSingleResult();
		entity.setSerial(nextSerial());
		super.store(entity);
	}

	private String nextSerial() {
		String prefix = LocalDate.now().format(SERIAL_PREFIX);
		String latest = entityManager
				.createQuery("SELECT MAX(b.serial) FROM CustomerEntity b WHERE b.serial LIKE :prefix", String.class)
				.setParameter("prefix", prefix + "%")
				.getSingleResult();
		int sequence = latest == null ? 1 : Integer.parseInt(latest.substring(prefix.length())) + 1;
		return prefix + String.format("%0" + SERIAL_DIGITS + "d", sequence);
	}

}
