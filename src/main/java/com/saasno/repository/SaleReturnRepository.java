package com.saasno.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.SaleReturnEntity;
import com.saasno.entity.SaleReturnItemEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;

@Repository
public class SaleReturnRepository extends JpaRepository<SaleReturnEntity, String> {

	private static final DateTimeFormatter SERIAL_DAY = DateTimeFormatter.ofPattern("yyMMdd");
	private static final int SERIAL_DIGITS = 4;

	public SaleReturnRepository() {
		super(SaleReturnEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<SaleReturnEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<SaleReturnEntity>(entityManager);
	}

	public DataResult<SaleReturnEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(SaleReturnEntity.class, dataFilters);
	}

	public List<SaleReturnItemEntity> findItems(Collection<String> returnIds) {
		if (returnIds == null || returnIds.isEmpty()) {
			return List.of();
		}
		return entityManager.createQuery(
				"SELECT i FROM SaleReturnItemEntity i WHERE i.returnId IN :ids ORDER BY i.saleItemId",
				SaleReturnItemEntity.class).setParameter("ids", returnIds).getResultList();
	}

	public void addItem(SaleReturnItemEntity item) {
		entityManager.persist(item);
	}

	// Nomor retur: RT/<serial cabang>/<yyMMdd>/<urutan harian 4 digit>.
	public void store(SaleReturnEntity entity, String branchSerial) {
		String prefix = "RT/" + branchSerial + "/" + LocalDate.now().format(SERIAL_DAY) + "/";
		entityManager.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:key))) l")
				.setParameter("key", "sale_return_serial:" + prefix).getSingleResult();
		String latest = entityManager
				.createQuery("SELECT MAX(r.serial) FROM SaleReturnEntity r WHERE r.serial LIKE :prefix", String.class)
				.setParameter("prefix", prefix + "%").getSingleResult();
		int sequence = latest == null ? 1 : Integer.parseInt(latest.substring(prefix.length())) + 1;
		entity.setSerial(prefix + String.format("%0" + SERIAL_DIGITS + "d", sequence));
		entityManager.persist(entity);
	}

}
