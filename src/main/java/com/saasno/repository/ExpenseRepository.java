package com.saasno.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.StoreExpenseEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;

@Repository
public class ExpenseRepository extends JpaRepository<StoreExpenseEntity, String> {

	private static final DateTimeFormatter SERIAL_DAY = DateTimeFormatter.ofPattern("yyMMdd");
	private static final int SERIAL_DIGITS = 3;

	public ExpenseRepository() {
		super(StoreExpenseEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<StoreExpenseEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<StoreExpenseEntity>(entityManager);
	}

	public DataResult<StoreExpenseEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(StoreExpenseEntity.class, dataFilters);
	}

	// Nomor biaya: EXP/<serial cabang>/<yyMMdd>/<urutan harian 3 digit>.
	public void store(StoreExpenseEntity entity, String branchSerial) {
		String prefix = "EXP/" + branchSerial + "/" + LocalDate.now().format(SERIAL_DAY) + "/";
		entityManager.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:key))) l")
				.setParameter("key", "expense_serial:" + prefix).getSingleResult();
		String latest = entityManager
				.createQuery("SELECT MAX(e.serial) FROM StoreExpenseEntity e WHERE e.serial LIKE :prefix", String.class)
				.setParameter("prefix", prefix + "%").getSingleResult();
		int sequence = latest == null ? 1 : Integer.parseInt(latest.substring(prefix.length())) + 1;
		entity.setSerial(prefix + String.format("%0" + SERIAL_DIGITS + "d", sequence));
		entityManager.persist(entity);
	}

}
