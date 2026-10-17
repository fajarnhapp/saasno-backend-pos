package com.saasno.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.ApiEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;

@Repository
public class ApiRepository extends JpaRepository<ApiEntity, String> {

    public ApiRepository() {
        super(ApiEntity.class);
    }

    @Autowired
    private EntityManager entityManager;
    private DataTable<ApiEntity> dataTable;

    @Override
    public EntityManager getEntityManager() {
        return entityManager;
    }

    @PostConstruct
    public void init() {
        dataTable = new DataTable<ApiEntity>(entityManager);
    }

    public DataResult<ApiEntity> dataTable(DataFilter dataFilters) {
        return dataTable.getData(ApiEntity.class, dataFilters);
    }

    public List<ApiEntity> findByMethod(String method) {
        return entityManager
                .createQuery("SELECT a FROM ApiEntity a WHERE UPPER(a.method) = :method", ApiEntity.class)
                .setParameter("method", method.toUpperCase())
                .getResultList();
    }

}
