package com.saasno.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.MenuEntity;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

@Repository
public class MenuRepository extends JpaRepository<MenuEntity, String> {

	public MenuRepository() {
		super(MenuEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<MenuEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<MenuEntity>(entityManager);
	}

	public DataResult<MenuEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(MenuEntity.class, dataFilters);
	}

	public MenuEntity findByParent(String parent) {
		TypedQuery<MenuEntity> query = entityManager.createNamedQuery("MenuEntity.findByParent", MenuEntity.class);
		query.setParameter("parent", parent);
		try {
			return query.getSingleResult();
		} catch (NoResultException e) {
			return null;
		} catch (Exception e) {
			Log.error("Error findByParent : ", e);
			return null;
		}
	}

}
