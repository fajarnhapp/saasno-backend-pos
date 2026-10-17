package com.saasno.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.ConfigurationEntity;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;

@Repository
public class ConfigurationRepository extends JpaRepository<ConfigurationEntity, String> {

	public ConfigurationRepository() {
		super(ConfigurationEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<ConfigurationEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<ConfigurationEntity>(entityManager);
	}

	public DataResult<ConfigurationEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(ConfigurationEntity.class, dataFilters);
	}

	public String get(ConfigurationKey id) {
		ConfigurationEntity config = find(id.name());
		if (config == null) {
			return null;
		}

		return config.getValue();
	}

}
