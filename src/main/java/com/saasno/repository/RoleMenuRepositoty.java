package com.saasno.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.RoleMenuEntity;
import com.saasno.entity.RoleMenuId;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

@Repository
public class RoleMenuRepositoty extends JpaRepository<RoleMenuEntity, String> {

	public RoleMenuRepositoty() {
		super(RoleMenuEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<RoleMenuEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<RoleMenuEntity>(entityManager);
	}

	public DataResult<RoleMenuEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(RoleMenuEntity.class, dataFilters);
	}

	public RoleMenuEntity findById(RoleMenuId id) {
		TypedQuery<RoleMenuEntity> query = entityManager.createNamedQuery("RoleMenuEntity.findById",
				RoleMenuEntity.class);
		query.setParameter("roleId", id.getRoleId());
		query.setParameter("menuId", id.getMenuId());
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
