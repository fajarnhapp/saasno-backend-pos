package com.saasno.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.ContactEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;

@Repository
public class ContactRepository extends JpaRepository<ContactEntity, String> {

	public ContactRepository() {
		super(ContactEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<ContactEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<ContactEntity>(entityManager);
	}

	public DataResult<ContactEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(ContactEntity.class, dataFilters);
	}

}
