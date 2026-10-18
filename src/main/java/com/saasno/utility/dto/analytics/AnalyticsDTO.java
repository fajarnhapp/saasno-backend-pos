package com.saasno.utility.dto.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Bentuk respons halaman Analitik. Semua nilai uang dalam rupiah (tanpa pajak bila disebut penjualan/omzet), persen dalam
// angka 0-100 dengan 1 desimal. Semua daftar sudah diurutkan dan dibatasi di server.
public final class AnalyticsDTO {

	private AnalyticsDTO() {
	}

	// ------------------------------------------------------------------ keuangan

	public record Totals(BigDecimal revenue, BigDecimal cogs, BigDecimal grossProfit, BigDecimal cashExpense,
			BigDecimal stockUseExpense, BigDecimal operatingResult, double grossMarginPercent) {
	}

	public record DayPoint(LocalDate date, BigDecimal revenue, BigDecimal cogs, BigDecimal grossProfit, BigDecimal expense,
			BigDecimal operatingResult) {
	}

	// kind: TOTAL (batang penuh dari nol) atau DELTA (menambah/mengurangi).
	public record Step(String label, BigDecimal amount, String kind) {
	}

	public record ExpenseCategory(String category, String label, BigDecimal amount, double percentOfRevenue) {
	}

	public record BreakEven(BigDecimal avgDailyExpense, double grossMarginPercent, BigDecimal requiredDailyRevenue,
			BigDecimal avgDailyRevenue, boolean reached) {
	}

	public record Projection(LocalDate monthStart, int daysElapsed, int daysInMonth, BigDecimal revenueToDate,
			BigDecimal resultToDate, BigDecimal projectedRevenue, BigDecimal projectedResult) {
	}

	public record Finance(Totals summary, Totals previous, List<DayPoint> daily, List<Step> waterfall,
			List<ExpenseCategory> expenseByCategory, BreakEven breakEven, Projection projection, long unknownCostLines) {
	}

	// ------------------------------------------------------------------ produk & stok

	public record AbcRow(String productId, String serial, String name, BigDecimal revenue, double share,
			double cumulativeShare, String cls) {
	}

	public record AbcClass(String cls, long products, BigDecimal revenue, double share) {
	}

	// quadrant: STAR (laris, margin tebal), COW (laris, margin tipis), QUESTION (jarang, margin tebal), DOG (jarang, tipis).
	public record MatrixPoint(String productId, String serial, String name, BigDecimal quantity, BigDecimal revenue,
			BigDecimal margin, double marginPercent, String quadrant) {
	}

	public record DeadStock(String productId, String serial, String name, BigDecimal quantity, BigDecimal value,
			LocalDate lastSold, Long daysSince) {
	}

	public record CoverRow(String productId, String serial, String name, BigDecimal stock, BigDecimal avgDaily,
			Double daysCover, LocalDate stockoutDate, BigDecimal suggestedReorder) {
	}

	public record Turnover(BigDecimal cogs, BigDecimal inventoryValue, double turns, Double daysOfInventory) {
	}

	public record OpnameRow(String productId, String serial, String name, long times, BigDecimal netDifference,
			BigDecimal absDifference, BigDecimal value) {
	}

	public record ReturnRow(String productId, String serial, String name, BigDecimal sold, BigDecimal returned,
			double ratePercent) {
	}

	public record Inventory(List<AbcClass> abcClasses, List<AbcRow> abc, List<MatrixPoint> matrix, BigDecimal medianQuantity,
			double medianMarginPercent, int deadDays, List<DeadStock> deadStock, BigDecimal deadStockValue,
			List<CoverRow> cover, Turnover turnover, List<OpnameRow> opname, List<ReturnRow> returns) {
	}

	// ------------------------------------------------------------------ penjualan

	public record HeatCell(int dow, int hour, long count, BigDecimal revenue) {
	}

	public record BasketDay(LocalDate date, long transactions, BigDecimal avgValue, double avgItems) {
	}

	public record Basket(long transactions, BigDecimal avgValue, double avgItems, List<BasketDay> daily) {
	}

	public record ProductPair(String a, String b, long together, double supportPercent, double confidencePercent,
			double lift) {
	}

	public record StatusCount(String status, long count, BigDecimal amount) {
	}

	public record ReasonCount(String reason, long count, BigDecimal amount) {
	}

	public record Holds(long created, long used, long discarded, long stillHeld) {
	}

	public record TopItem(String name, BigDecimal quantity) {
	}

	public record Online(List<StatusCount> funnel, Double avgDecisionMinutes, BigDecimal onlineRevenue,
			BigDecimal posRevenue, double onlineSharePercent, List<TopItem> topProducts) {
	}

	public record Sales(List<HeatCell> heatmap, Basket basket, List<ProductPair> pairs, List<StatusCount> statuses,
			List<ReasonCount> voidReasons, Holds holds, Online online) {
	}

	// ------------------------------------------------------------------ promo & diskon

	public record PromoRow(String promoId, String name, long uses, BigDecimal discountGiven, BigDecimal revenue,
			BigDecimal avgBasket, double discountRatePercent, BigDecimal avgDailyDuring, BigDecimal avgDailyBefore,
			Double changePercent) {
	}

	public record PromoBaseline(BigDecimal avgBasketAll, BigDecimal avgBasketPromo, BigDecimal avgBasketNoPromo,
			double promoSharePercent) {
	}

	public record CashierDiscount(String userId, String username, long transactions, long withDiscount,
			BigDecimal totalDiscount, double discountPercentOfSubtotal) {
	}

	public record Approvals(long waiting, long approved, long rejected, Double avgDecisionMinutes) {
	}

	public record Promo(List<PromoRow> promos, PromoBaseline baseline, List<CashierDiscount> cashiers,
			List<ReasonCount> reasons, Approvals approvals) {
	}

	// ------------------------------------------------------------------ customer

	public record RfmRow(String customerId, String serial, String name, String type, long recencyDays, long frequency,
			BigDecimal monetary, int r, int f, int m, String segment) {
	}

	public record Segment(String segment, long customers, BigDecimal monetary) {
	}

	public record Lapsing(String customerId, String serial, String name, String type, LocalDate lastPurchase,
			long daysSince, long purchases, double avgIntervalDays, BigDecimal monetary) {
	}

	public record NewReturningDay(LocalDate date, long newCustomers, long returning, long anonymous) {
	}

	public record CustomerType(String type, long customers, long transactions, BigDecimal revenue, BigDecimal avgBasket,
			double marginPercent, double specialPriceSharePercent) {
	}

	public record Customers(List<Segment> segments, List<RfmRow> rfm, List<Lapsing> lapsing, List<NewReturningDay> newVsReturning,
			List<CustomerType> byType, long anonymousTransactions, long identifiedTransactions) {
	}

	// ------------------------------------------------------------------ cabang & kasir

	public record BranchRow(String branchId, String name, BigDecimal revenue, BigDecimal grossProfit, double marginPercent,
			BigDecimal operatingResult, long transactions, BigDecimal avgBasket, double returnRatePercent, long voids,
			BigDecimal opnameAbsValue) {
	}

	public record CashierRow(String userId, String username, long transactions, BigDecimal revenue, BigDecimal avgBasket,
			long voids, double voidRatePercent, BigDecimal manualDiscount, double discountPercentOfSubtotal, long offHours,
			long returnsProcessed) {
	}

	public record Flow(String sourceId, String sourceName, String destinationId, String destinationName, long transfers,
			BigDecimal quantity, BigDecimal value) {
	}

	// severity: HIGH atau MEDIUM; kind: kode aturan; subject: kasir/cabang yang dimaksud.
	public record Anomaly(String kind, String severity, String subject, String detail) {
	}

	public record PettyCash(String branchId, String name, BigDecimal topUp, BigDecimal expense, long overThreshold,
			BigDecimal balance) {
	}

	public record Branches(List<BranchRow> branches, List<CashierRow> cashiers, List<Flow> flows, List<Anomaly> anomalies,
			List<PettyCash> pettyCash, int openHour, int closeHour, boolean open24Hours) {
	}
}
