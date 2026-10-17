package com.saasno.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.StockBalanceEntity;
import com.saasno.entity.StockBalanceId;
import com.saasno.entity.StockMovementEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;

// Saldo dan mutasi stok. Saldo diubah hanya lewat increase/decrease (SQL atomik di dalam transaksi
// pemanggil); setiap perubahan harus diikuti baris mutasi lewat addMovement.
@Repository
public class StockRepository extends JpaRepository<StockBalanceEntity, StockBalanceId> {

	public StockRepository() {
		super(StockBalanceEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<StockBalanceEntity> balanceTable;
	private DataTable<StockMovementEntity> movementTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		balanceTable = new DataTable<StockBalanceEntity>(entityManager);
		movementTable = new DataTable<StockMovementEntity>(entityManager);
	}

	public DataResult<StockBalanceEntity> balances(DataFilter filter) {
		return balanceTable.getData(StockBalanceEntity.class, filter);
	}

	public DataResult<StockMovementEntity> movements(DataFilter filter) {
		return movementTable.getData(StockMovementEntity.class, filter);
	}

	public void addMovement(StockMovementEntity movement) {
		entityManager.persist(movement);
	}

	// Saldo saat ini tanpa mengunci; 0 kalau belum ada baris.
	public BigDecimal quantity(String branchId, String productId) {
		return read(branchId, productId, false);
	}

	// Menjamin baris saldo ada lalu menguncinya sampai transaksi selesai (dipakai koreksi stok).
	public BigDecimal lockQuantity(String branchId, String productId) {
		entityManager.createNativeQuery(
				"INSERT INTO public.stock_balance (branch_id, product_id, quantity, modificated) "
						+ "VALUES (:branch, :product, 0, :now) ON CONFLICT (branch_id, product_id) DO NOTHING")
				.setParameter("branch", branchId).setParameter("product", productId)
				.setParameter("now", LocalDateTime.now()).executeUpdate();
		return read(branchId, productId, true);
	}

	// Menambah saldo (membuat baris kalau belum ada) dan mengembalikan saldo baru.
	public BigDecimal increase(String branchId, String productId, BigDecimal amount) {
		entityManager.createNativeQuery(
				"INSERT INTO public.stock_balance (branch_id, product_id, quantity, modificated) "
						+ "VALUES (:branch, :product, :amount, :now) "
						+ "ON CONFLICT (branch_id, product_id) DO UPDATE "
						+ "SET quantity = public.stock_balance.quantity + EXCLUDED.quantity, modificated = EXCLUDED.modificated")
				.setParameter("branch", branchId).setParameter("product", productId)
				.setParameter("amount", amount).setParameter("now", LocalDateTime.now()).executeUpdate();
		return read(branchId, productId, false);
	}

	// Mengurangi saldo hanya kalau cukup; mengembalikan saldo baru, atau null kalau stok tidak cukup.
	// Syarat "quantity >= amount" ada di UPDATE itu sendiri, jadi dua pengurangan bersamaan tidak
	// pernah membuat saldo minus.
	public BigDecimal decrease(String branchId, String productId, BigDecimal amount) {
		int updated = entityManager.createNativeQuery(
				"UPDATE public.stock_balance SET quantity = quantity - :amount, modificated = :now "
						+ "WHERE branch_id = :branch AND product_id = :product AND quantity >= :amount")
				.setParameter("branch", branchId).setParameter("product", productId)
				.setParameter("amount", amount).setParameter("now", LocalDateTime.now()).executeUpdate();
		return updated == 0 ? null : read(branchId, productId, false);
	}

	// Jumlah baris saldo dan mutasi yang menyangkut produk ini.
	public long countByProduct(String productId) {
		Number total = (Number) entityManager.createNativeQuery(
				"SELECT (SELECT COUNT(*) FROM public.stock_balance WHERE product_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.stock_movement WHERE product_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.stock_transfer_item WHERE product_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.stock_opname_item WHERE product_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.online_order_item WHERE product_id = :id)"
						+ " + (SELECT COUNT(*) FROM public.sale_item WHERE product_id = :id)")
				.setParameter("id", productId).getSingleResult();
		return total.longValue();
	}

	private BigDecimal read(String branchId, String productId, boolean lock) {
		var query = entityManager.createNativeQuery(
				"SELECT quantity FROM public.stock_balance WHERE branch_id = :branch AND product_id = :product"
						+ (lock ? " FOR UPDATE" : ""))
				.setParameter("branch", branchId).setParameter("product", productId);
		var rows = query.getResultList();
		return rows.isEmpty() ? BigDecimal.ZERO : (BigDecimal) rows.get(0);
	}

}
