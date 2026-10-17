package com.saasno.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.BranchContactEntity;
import com.saasno.entity.BranchContactId;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

@Repository
public class BranchContactRepository extends JpaRepository<BranchContactEntity, String> {

	public BranchContactRepository() {
		super(BranchContactEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<BranchContactEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<BranchContactEntity>(entityManager);
	}

	public DataResult<BranchContactEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(BranchContactEntity.class, dataFilters);
	}

	public BranchContactEntity findById(BranchContactId id) {
		TypedQuery<BranchContactEntity> query = entityManager.createNamedQuery("BranchContactEntity.findById", BranchContactEntity.class);
		query.setParameter("branchId", id.getBranchId());
		query.setParameter("contactId", id.getContactId());
		try {
			return query.getSingleResult();
		} catch (NoResultException e) {
			return null;
		} catch (Exception e) {
			Log.error("Error findById : ", e);
			return null;
		}
	}

	public List<BranchContactEntity> findByBranchIds(Collection<String> branchIds) {
		if (branchIds == null || branchIds.isEmpty()) {
			return List.of();
		}
		return entityManager.createQuery(
				"SELECT bc FROM BranchContactEntity bc JOIN FETCH bc.contact WHERE bc.branch.id IN :ids",
				BranchContactEntity.class).setParameter("ids", branchIds).getResultList();
	}

}
