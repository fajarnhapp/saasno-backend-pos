package com.saasno.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.RoleApiEntity;
import com.saasno.entity.RoleApiId;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

@Repository
public class RoleApiRepository extends JpaRepository<RoleApiEntity, String> {

	public RoleApiRepository() {
		super(RoleApiEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<RoleApiEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<RoleApiEntity>(entityManager);
	}

	public DataResult<RoleApiEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(RoleApiEntity.class, dataFilters);
	}

	public RoleApiEntity findById(RoleApiId id) {
		TypedQuery<RoleApiEntity> query = entityManager.createNamedQuery("RoleApiEntity.findById", RoleApiEntity.class);
		query.setParameter("roleId", id.getRoleId());
		query.setParameter("apiId", id.getApiId());
		try {
			return query.getSingleResult();
		} catch (NoResultException e) {
			return null;
		} catch (Exception e) {
			Log.error("Error findById : ", e);
			return null;
		}
	}

}
