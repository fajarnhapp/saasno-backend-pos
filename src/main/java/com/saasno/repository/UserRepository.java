package com.saasno.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.UserEntity;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

@Repository
public class UserRepository extends JpaRepository<UserEntity, String> {

	public UserRepository() {
		super(UserEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<UserEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<UserEntity>(entityManager);
	}

	public DataResult<UserEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(UserEntity.class, dataFilters);
	}

	public UserEntity findByUsername(String username) {
		TypedQuery<UserEntity> query = entityManager.createNamedQuery("UserEntity.findByUsername", UserEntity.class);
		query.setParameter("username", username);
		try {
			return query.getSingleResult();
		} catch (NoResultException e) {
			return null;
		} catch (Exception e) {
			Log.error("Error findByUsername : ", e);
			return null;
		}
	}

	// id -> username, untuk menampilkan nama pembuat/pemutus di riwayat.
	public java.util.Map<String, String> findUsernames(java.util.Collection<String> ids) {
		java.util.Map<String, String> names = new java.util.HashMap<>();
		if (ids == null || ids.isEmpty()) {
			return names;
		}
		entityManager.createQuery("SELECT u.id, u.username FROM UserEntity u WHERE u.id IN :ids", Object[].class)
				.setParameter("ids", ids).getResultList()
				.forEach(row -> names.put((String) row[0], (String) row[1]));
		return names;
	}

	// Jumlah catatan stok dan transaksi (mutasi, oper stok, penjualan, retur) yang dibuat atau diputuskan user ini.
	// Notifikasi dan penghitung milik user yang dihapus ikut dibersihkan (tidak ada FK ke tabel user).
	public void deleteNotifications(String userId) {
		entityManager.createNativeQuery("DELETE FROM public.notification WHERE user_id = :id").setParameter("id", userId).executeUpdate();
		entityManager.createNativeQuery("DELETE FROM public.notification_unread WHERE user_id = :id").setParameter("id", userId).executeUpdate();
		entityManager.createNativeQuery("DELETE FROM public.notification_mute WHERE user_id = :id").setParameter("id", userId).executeUpdate();
	}

	public long countStockActivity(String userId) {
		Number total = (Number) entityManager.createNativeQuery(
				"SELECT (SELECT COUNT(*) FROM public.stock_movement WHERE created_by = :id)"
						+ " + (SELECT COUNT(*) FROM public.stock_transfer WHERE requested_by = :id OR decided_by = :id)"
						+ " + (SELECT COUNT(*) FROM public.stock_opname WHERE requested_by = :id OR decided_by = :id)"
						+ " + (SELECT COUNT(*) FROM public.online_order WHERE decided_by = :id)"
						+ " + (SELECT COUNT(*) FROM public.online_order_event WHERE actor = :id)"
						+ " + (SELECT COUNT(*) FROM public.sale WHERE created_by = :id OR decided_by = :id OR voided_by = :id)"
						+ " + (SELECT COUNT(*) FROM public.sale_return WHERE created_by = :id)"
						+ " + (SELECT COUNT(*) FROM public.sale_hold WHERE created_by = :id OR used_by = :id OR discarded_by = :id)"
						+ " + (SELECT COUNT(*) FROM public.store_expense WHERE created_by = :id OR voided_by = :id)"
						+ " + (SELECT COUNT(*) FROM public.petty_cash_entry WHERE created_by = :id)"
						+ " + (SELECT COUNT(*) FROM public.article WHERE created_by = :id OR updated_by = :id)")
				.setParameter("id", userId).getSingleResult();
		return total.longValue();
	}

}
