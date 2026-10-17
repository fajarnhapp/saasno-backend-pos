package com.saasno.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.DownloadJobEntity;
import com.saasno.utility.logger.Log;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

@Repository
public class DownloadJobRepository extends JpaRepository<DownloadJobEntity, String> {

    public DownloadJobRepository() {
        super(DownloadJobEntity.class);
    }

    @Autowired
    private EntityManager entityManager;

    @Override
    public EntityManager getEntityManager() {
        return entityManager;
    }

    public List<DownloadJobEntity> findByRequestedBy(String requestedBy, int limit) {
        TypedQuery<DownloadJobEntity> query = entityManager.createQuery(
                "SELECT e FROM DownloadJobEntity e WHERE e.requestedBy = :requestedBy ORDER BY e.registered DESC",
                DownloadJobEntity.class);
        query.setParameter("requestedBy", requestedBy);
        query.setMaxResults(limit);
        try {
            return query.getResultList();
        } catch (Exception e) {
            Log.error("Error findByRequestedBy : ", e);
            return List.of();
        }
    }

}
