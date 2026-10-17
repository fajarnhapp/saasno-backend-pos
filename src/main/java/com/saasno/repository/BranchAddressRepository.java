package com.saasno.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.BranchAddressEntity;
import com.saasno.entity.BranchAddressId;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

@Repository
public class BranchAddressRepository extends JpaRepository<BranchAddressEntity, String> {

	public BranchAddressRepository() {
		super(BranchAddressEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<BranchAddressEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<BranchAddressEntity>(entityManager);
	}

	public DataResult<BranchAddressEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(BranchAddressEntity.class, dataFilters);
	}

	public BranchAddressEntity findById(BranchAddressId id) {
		TypedQuery<BranchAddressEntity> query = entityManager.createNamedQuery("BranchAddressEntity.findById", BranchAddressEntity.class);
		query.setParameter("branchId", id.getBranchId());
		query.setParameter("addressId", id.getAddressId());
		try {
			return query.getSingleResult();
		} catch (NoResultException e) {
			return null;
		} catch (Exception e) {
			Log.error("Error findById : ", e);
			return null;
		}
	}

	public List<BranchAddressEntity> findByBranchIds(Collection<String> branchIds) {
		if (branchIds == null || branchIds.isEmpty()) {
			return List.of();
		}
		return entityManager.createQuery(
				"SELECT ba FROM BranchAddressEntity ba JOIN FETCH ba.address WHERE ba.branch.id IN :ids",
				BranchAddressEntity.class).setParameter("ids", branchIds).getResultList();
	}

}
