package com.saasno.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.NotificationEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;

@Repository
public class NotificationRepository extends JpaRepository<NotificationEntity, String> {

    public NotificationRepository() {
        super(NotificationEntity.class);
    }

    @Autowired
    private EntityManager entityManager;
    private DataTable<NotificationEntity> dataTable;

    @Override
    public EntityManager getEntityManager() {
        return entityManager;
    }

    @PostConstruct
    public void init() {
        dataTable = new DataTable<NotificationEntity>(entityManager);
    }

    public DataResult<NotificationEntity> dataTable(DataFilter dataFilters) {
        return dataTable.getData(NotificationEntity.class, dataFilters);
    }
}
