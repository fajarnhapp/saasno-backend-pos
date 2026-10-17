package com.saasno.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.NotificationMessageEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;

@Repository
public class NotificationMessageRepository extends JpaRepository<NotificationMessageEntity, String> {

    public NotificationMessageRepository() {
        super(NotificationMessageEntity.class);
    }

    @Autowired
    private EntityManager entityManager;
    private DataTable<NotificationMessageEntity> dataTable;

    @Override
    public EntityManager getEntityManager() {
        return entityManager;
    }

    @PostConstruct
    public void init() {
        dataTable = new DataTable<NotificationMessageEntity>(entityManager);
    }

    public DataResult<NotificationMessageEntity> dataTable(DataFilter dataFilters) {
        return dataTable.getData(NotificationMessageEntity.class, dataFilters);
    }
}
