package com.saasno.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.ProductPriceTierEntity;

import jakarta.persistence.EntityManager;

@Repository
public class ProductPriceTierRepository extends JpaRepository<ProductPriceTierEntity, String> {

	public ProductPriceTierRepository() {
		super(ProductPriceTierEntity.class);
	}

	@Autowired
	private EntityManager entityManager;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	public List<ProductPriceTierEntity> findByProductIds(Collection<String> productIds) {
		if (productIds == null || productIds.isEmpty()) {
			return List.of();
		}
		return entityManager.createQuery(
				"SELECT t FROM ProductPriceTierEntity t WHERE t.productId IN :ids ORDER BY t.minQuantity",
				ProductPriceTierEntity.class).setParameter("ids", productIds).getResultList();
	}

	public void deleteByProductId(String productId) {
		entityManager.createQuery("DELETE FROM ProductPriceTierEntity t WHERE t.productId = :productId")
				.setParameter("productId", productId).executeUpdate();
	}

}
