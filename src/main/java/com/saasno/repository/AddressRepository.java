package com.saasno.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.AddressEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;

@Repository
public class AddressRepository extends JpaRepository<AddressEntity, String> {

	public AddressRepository() {
		super(AddressEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<AddressEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<AddressEntity>(entityManager);
	}

	public DataResult<AddressEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(AddressEntity.class, dataFilters);
	}

}
