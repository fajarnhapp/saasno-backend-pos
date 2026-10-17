package com.saasno.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.ProductUnitConversionEntity;

import jakarta.persistence.EntityManager;

@Repository
public class ProductUnitConversionRepository extends JpaRepository<ProductUnitConversionEntity, String> {

	public ProductUnitConversionRepository() {
		super(ProductUnitConversionEntity.class);
	}

	@Autowired
	private EntityManager entityManager;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	public List<ProductUnitConversionEntity> findByProductIds(Collection<String> productIds) {
		if (productIds == null || productIds.isEmpty()) {
			return List.of();
		}
		return entityManager.createQuery(
				"SELECT c FROM ProductUnitConversionEntity c WHERE c.productId IN :ids ORDER BY c.registered, c.toUnit",
				ProductUnitConversionEntity.class).setParameter("ids", productIds).getResultList();
	}

	public List<String> findUsedUnits() {
		return entityManager.createQuery("SELECT DISTINCT c.toUnit FROM ProductUnitConversionEntity c", String.class)
				.getResultList();
	}

	public void deleteByProductId(String productId) {
		entityManager.createQuery("DELETE FROM ProductUnitConversionEntity c WHERE c.productId = :productId")
				.setParameter("productId", productId).executeUpdate();
	}

}
