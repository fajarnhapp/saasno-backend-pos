package com.saasno.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.RoleEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;

@Repository
public class RoleRepository extends JpaRepository<RoleEntity, String> {

	public RoleRepository() {
		super(RoleEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<RoleEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<RoleEntity>(entityManager);
	}

	public DataResult<RoleEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(RoleEntity.class, dataFilters);
	}

	public long countUsers(String roleId) {
		return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM public.user_role WHERE role_id = :id")
				.setParameter("id", roleId).getSingleResult()).longValue();
	}

}
