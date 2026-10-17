package com.saasno.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.ProductCustomerPriceEntity;

import jakarta.persistence.EntityManager;

@Repository
public class ProductCustomerPriceRepository extends JpaRepository<ProductCustomerPriceEntity, String> {

	public ProductCustomerPriceRepository() {
		super(ProductCustomerPriceEntity.class);
	}

	@Autowired
	private EntityManager entityManager;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	public List<ProductCustomerPriceEntity> findByProductIds(Collection<String> productIds) {
		if (productIds == null || productIds.isEmpty()) {
			return List.of();
		}
		return entityManager.createQuery(
				"SELECT p FROM ProductCustomerPriceEntity p WHERE p.productId IN :ids ORDER BY p.target DESC, p.registered",
				ProductCustomerPriceEntity.class).setParameter("ids", productIds).getResultList();
	}

	public void deleteByProductId(String productId) {
		entityManager.createQuery("DELETE FROM ProductCustomerPriceEntity p WHERE p.productId = :productId")
				.setParameter("productId", productId).executeUpdate();
	}

	// Jumlah aturan harga khusus yang menunjuk customer ini.
	public long countByCustomer(String customerId) {
		return entityManager.createQuery(
				"SELECT COUNT(p) FROM ProductCustomerPriceEntity p WHERE p.customerId = :id", Long.class)
				.setParameter("id", customerId).getSingleResult();
	}

}
