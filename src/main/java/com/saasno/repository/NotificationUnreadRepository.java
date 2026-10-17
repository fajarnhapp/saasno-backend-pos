package com.saasno.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.NotificationUnreadEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

@Repository
public class NotificationUnreadRepository extends JpaRepository<NotificationUnreadEntity, String> {

    public NotificationUnreadRepository() {
        super(NotificationUnreadEntity.class);
    }

    @Autowired
    private EntityManager entityManager;

    @Override
    public EntityManager getEntityManager() {
        return entityManager;
    }

    @Transactional
    public int incrementTotalByUserId(String userId) {
        Query query = entityManager.createNamedQuery("NotificationUnreadEntity.incrementTotalByUserId");
        query.setParameter("userId", userId);
        try {
            return query.executeUpdate();
        } catch (Exception e) {
            System.err.println("Error incrementTotalByUserId: " + e.getMessage());
            return 0;
        }
    }
}
