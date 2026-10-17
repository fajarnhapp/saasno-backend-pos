package com.saasno.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.ConfigurationEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.dto.finance.ExpenseDTO;
import com.saasno.utility.dto.finance.FinanceReportDTO;
import com.saasno.utility.dto.finance.PettyCashEntryDTO;
import com.saasno.utility.dto.order.OrderDTO;
import com.saasno.utility.dto.sale.SaleCashDTO;
import com.saasno.utility.dto.sale.SaleChargeDTO;
import com.saasno.utility.dto.sale.SaleDTO;
import com.saasno.utility.dto.sale.SaleReturnDTO;
import com.saasno.utility.dto.stock.StockDTO;
import com.saasno.utility.dto.stock.StockMovementDTO;
import com.saasno.utility.dto.stock.StockOpnameDTO;
import com.saasno.utility.dto.stock.StockTransferDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

// Isi tabel untuk unduhan halaman transaksi, stok, keuangan, dan konfigurasi. Memakai layanan baca yang sama dengan halamannya
// (cakupan cabang dan filter sama), lalu menjadikannya baris teks yang mudah dibaca: tanggal "2026-10-08 13:14",
// status berbahasa Indonesia, dan satuan memakai namanya.
@Service
public class ExportTableService {

	public record Table(String[] headers, List<String[]> rows) {
	}

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final int ORDER_PAGE = 100;

	private static final Map<String, String> STATUS = Map.ofEntries(
			Map.entry("PENDING", "Menunggu"), Map.entry("APPROVED", "Disetujui"), Map.entry("REJECTED", "Ditolak"),
			Map.entry("CANCELLED", "Dibatalkan"), Map.entry("COMPLETED", "Selesai"), Map.entry("READY", "Siap"),
			Map.entry("WAITING_APPROVAL", "Menunggu persetujuan"), Map.entry("VOIDED", "Dibatalkan setelah selesai"));
	private static final Map<String, String> MOVEMENT = Map.ofEntries(
			Map.entry("IN", "Stok masuk"), Map.entry("OUT", "Stok keluar"), Map.entry("SALE", "Penjualan"),
			Map.entry("RETURN", "Retur"), Map.entry("ADJUST", "Koreksi"), Map.entry("USE", "Pemakaian"),
			Map.entry("TRANSFER_IN", "Transfer masuk"), Map.entry("TRANSFER_OUT", "Transfer keluar"), Map.entry("VOID", "Pembatalan"));
	private static final Map<String, String> PETTY = Map.of("TOPUP", "Isi kas", "EXPENSE", "Biaya", "VOID", "Pembatalan biaya");
	private static final Map<String, String> EXPENSE_KIND = Map.of("CASH", "Kas kecil", "STOCK_USE", "Pakai stok");

	@Autowired
	private SaleService saleService;
	@Autowired
	private SaleReturnService saleReturnService;
	@Autowired
	private OnlineOrderService orderService;
	@Autowired
	private ExpenseService expenseService;
	@Autowired
	private PettyCashService pettyCashService;
	@Autowired
	private StockService stockService;
	@Autowired
	private StockTransferService transferService;
	@Autowired
	private StockOpnameService opnameService;
	@Autowired
	private SaleReportService saleReportService;
	@Autowired
	private FinanceReportService financeReportService;
	@Autowired
	private ConfigurationRepository configurationRepository;
	@Autowired
	private ProductUnitService productUnitService;

	@Transactional(readOnly = true)
	public Table build(String type, Map<String, String> params, BranchScope scope) {
		Map<String, String> units = unitLabels();
		return switch (type) {
			case "SALE_EXPORT" -> sales(saleService.find(filter(params), scope).getData(), units);
			case "RETURN_EXPORT" -> returns(saleReturnService.find(filter(params), scope).getData(), units);
			case "ORDER_EXPORT" -> orders(allOrders(params, scope));
			case "EXPENSE_EXPORT" -> expenses(expenseService.find(filter(params), scope).getData(), units);
			case "PETTY_CASH_EXPORT" -> pettyCash(pettyCashService.find(filter(params), scope).getData());
			case "STOCK_EXPORT" -> stock(stockService.find(filter(params), scope).getData(), units);
			case "STOCK_MOVEMENT_EXPORT" -> movements(stockService.findMovements(filter(params), scope).getData(), units);
			case "STOCK_TRANSFER_EXPORT" -> transfers(transferService.find(filter(params), scope).getData(), units);
			case "STOCK_OPNAME_EXPORT" -> opnames(opnameService.find(filter(params), scope).getData(), units);
			case "SALE_CASH_EXPORT" -> cash(saleReportService.cash(date(params, "from"), date(params, "to"), params.get("branchId"), scope).getData());
			case "FINANCE_REPORT_EXPORT" -> finance(financeReportService.report(date(params, "from"), date(params, "to"), params.get("branchId"), scope).getData());
			case "CONFIGURATION_EXPORT" -> configuration(params);
			default -> throw new ApiException("400", "Jenis unduhan tidak dikenal");
		};
	}

	// ---------------------------------------------------------------- penjualan dan pesanan

	private Table sales(List<SaleDTO> list, Map<String, String> units) {
		return new Table(new String[] { "Nomor", "Tanggal", "Cabang", "Pelanggan", "Status", "Kasir", "Barang", "Subtotal", "Promo",
				"Diskon Promo", "Diskon Manual", "Alasan Diskon", "Biaya Lain-lain", "Jumlah Biaya", "Total", "Dibayar", "Kembalian",
				"Diputuskan Oleh", "Catatan Keputusan", "Dibatalkan Oleh", "Alasan Pembatalan" },
				list.stream().map(s -> row(s.getSerial(), stamp(s.getRegistered()), s.getBranchName(), s.getCustomerName(),
						status(s.getStatus()), s.getCreatedByName(),
						join(s.getItems(), i -> i.getProductName() + " x " + num(i.getQuantity()) + " " + unit(units, i.getUnit())
								+ (Boolean.TRUE.equals(i.getFree()) ? " (gratis)" : "")),
						num(s.getSubtotal()), s.getPromoName(), num(s.getPromoDiscount()), num(s.getManualDiscount()),
						s.getManualDiscountReason(), join(s.getCharges(), this::chargeText), num(s.getChargeTotal()),
						num(s.getTotal()), num(s.getPaid()), num(s.getChangeAmount()), s.getDecidedByName(), s.getDecisionNote(),
						s.getVoidedByName(), s.getVoidReason())).toList());
	}

	private String chargeText(SaleChargeDTO c) {
		return c.label() + (c.rate() != null ? " " + num(c.rate()) + "%" : "") + (c.inclusive() ? " (termasuk)" : "") + ": " + num(c.amount());
	}

	private Table returns(List<SaleReturnDTO> list, Map<String, String> units) {
		return new Table(new String[] { "Nomor Retur", "Tanggal", "Nomor Penjualan", "Cabang", "Alasan", "Barang", "Total Pengembalian", "Dibuat Oleh" },
				list.stream().map(r -> row(r.getSerial(), stamp(r.getRegistered()), r.getSaleSerial(), r.getBranchName(), r.getReason(),
						join(r.getItems(), i -> i.getProductName() + " x " + num(i.getQuantity()) + " " + unit(units, i.getUnit())
								+ (Boolean.TRUE.equals(i.getRestock()) ? " (masuk stok)" : "")),
						num(r.getRefundTotal()), r.getCreatedByName())).toList());
	}

	// Pesanan online dibaca per halaman (batas halaman layanannya 100).
	private List<OrderDTO> allOrders(Map<String, String> params, BranchScope scope) {
		List<OrderDTO> all = new ArrayList<>();
		for (int offset = 0;; offset += ORDER_PAGE) {
			int start = offset;
			Function<String, String> source = key -> switch (key) {
				case "limit" -> String.valueOf(ORDER_PAGE);
				case "offset" -> String.valueOf(start);
				default -> params.get(key);
			};
			List<OrderDTO> page = orderService.find(source::apply, scope).getData();
			all.addAll(page);
			if (page.size() < ORDER_PAGE) {
				return all;
			}
		}
	}

	private Table orders(List<OrderDTO> list) {
		return new Table(new String[] { "Nomor Pesanan", "Tanggal", "Cabang", "Status", "Nama Pembeli", "Nomor HP", "Pengambilan", "Pembayaran",
				"Alamat", "Catatan Pembeli", "Barang", "Perkiraan Total", "Total Pasti", "Nomor Penjualan", "Diputuskan Oleh", "Catatan Keputusan" },
				list.stream().map(o -> row(o.serial(), stamp(o.registered()), o.branchName(), status(o.status()), o.customerName(), o.customerPhone(),
						"DELIVERY".equals(o.fulfillment()) ? "Diantar" : "Ambil di toko", "TRANSFER".equals(o.paymentMethod()) ? "Transfer" : "Tunai",
						o.address(), o.note(), join(o.items(), i -> i.productName() + " x " + num(i.quantity()) + " " + i.unitLabel()),
						num(o.estimateTotal()), num(o.confirmedTotal()), o.saleSerial(), o.decidedByName(), o.decisionNote())).toList());
	}

	// ---------------------------------------------------------------- keuangan

	private Table expenses(List<ExpenseDTO> list, Map<String, String> units) {
		return new Table(new String[] { "Nomor", "Tanggal", "Cabang", "Jenis", "Kategori", "Deskripsi", "Nominal", "Barang Dipakai", "Jumlah",
				"No. Nota", "Alasan", "Disetujui Oleh", "Melebihi Batas", "Status", "Dicatat Oleh", "Dibatalkan Oleh", "Alasan Pembatalan" },
				list.stream().map(e -> row(e.getSerial(), stamp(e.getRegistered()), e.getBranchName(), EXPENSE_KIND.getOrDefault(e.getKind(), e.getKind()),
						e.getCategory(), e.getDescription(), num(e.getAmount()), e.getProductName(),
						e.getQuantity() == null ? "" : num(e.getQuantity()) + " " + unit(units, e.getUnit()), e.getReceiptNo(), e.getReason(),
						e.getApprovedBy(), Boolean.TRUE.equals(e.getOverThreshold()) ? "Ya" : "Tidak",
						"VOIDED".equals(e.getStatus()) ? "Dibatalkan" : "Tercatat", e.getCreatedByName(), e.getVoidedByName(), e.getVoidReason())).toList());
	}

	private Table pettyCash(List<PettyCashEntryDTO> list) {
		return new Table(new String[] { "Tanggal", "Cabang", "Jenis", "Nominal", "Saldo Setelahnya", "Nomor Biaya", "Catatan", "Dicatat Oleh" },
				list.stream().map(p -> row(stamp(p.getRegistered()), p.getBranchName(), PETTY.getOrDefault(p.getType(), p.getType()), num(p.getAmount()),
						num(p.getBalanceAfter()), p.getExpenseSerial(), p.getNote(), p.getCreatedByName())).toList());
	}

	private Table cash(List<SaleCashDTO> list) {
		return new Table(new String[] { "Tanggal", "Cabang", "Jumlah Penjualan", "Total Penjualan", "Jumlah Batal", "Total Batal", "Jumlah Retur",
				"Total Retur", "Bersih", "Diskon Promo", "Diskon Manual", "Biaya Lain-lain" },
				list.stream().map(c -> row(String.valueOf(c.getDate()), c.getBranchName(), String.valueOf(c.getSaleCount()), num(c.getSalesTotal()),
						String.valueOf(c.getVoidCount()), num(c.getVoidTotal()), String.valueOf(c.getReturnCount()), num(c.getReturnTotal()),
						num(c.getNet()), num(c.getPromoDiscount()), num(c.getManualDiscount()), num(c.getChargeTotal()))).toList());
	}

	private Table finance(List<FinanceReportDTO> list) {
		return new Table(new String[] { "Tanggal", "Cabang", "Pendapatan", "Harga Pokok", "Laba Kotor", "Biaya Kas", "Biaya Pakai Stok",
				"Hasil Operasional", "Isi Kas Kecil", "Baris Tanpa Harga Pokok" },
				list.stream().map(f -> row(String.valueOf(f.getDate()), f.getBranchName(), num(f.getRevenue()), num(f.getCogs()), num(f.getGrossProfit()),
						num(f.getCashExpense()), num(f.getStockUseExpense()), num(f.getOperatingResult()), num(f.getPettyCashTopUp()),
						String.valueOf(f.getUnknownCostLines()))).toList());
	}

	// ---------------------------------------------------------------- stok

	private Table stock(List<StockDTO> list, Map<String, String> units) {
		return new Table(new String[] { "Cabang", "Serial Produk", "Produk", "Stok", "Satuan", "Terakhir Berubah" },
				list.stream().map(s -> row(s.getBranchName(), s.getProductSerial(), s.getProductName(), num(s.getQuantity()), unit(units, s.getUnit()),
						stamp(s.getModificated()))).toList());
	}

	private Table movements(List<StockMovementDTO> list, Map<String, String> units) {
		return new Table(new String[] { "Tanggal", "Cabang", "Serial Produk", "Produk", "Jenis", "Jumlah", "Satuan", "Stok Setelahnya", "Rujukan", "Catatan", "Dicatat Oleh" },
				list.stream().map(m -> row(stamp(m.getRegistered()), m.getBranchName(), m.getProductSerial(), m.getProductName(),
						MOVEMENT.getOrDefault(m.getType(), m.getType()), num(m.getQuantity()), unit(units, m.getUnit()), num(m.getBalanceAfter()),
						m.getReferenceType(), m.getNote(), m.getCreatedByName())).toList());
	}

	private Table transfers(List<StockTransferDTO> list, Map<String, String> units) {
		return new Table(new String[] { "Nomor", "Tanggal", "Dari Cabang", "Ke Cabang", "Status", "Barang", "Catatan", "Diajukan Oleh", "Diputuskan Oleh", "Waktu Keputusan", "Catatan Keputusan" },
				list.stream().map(t -> row(t.getSerial(), stamp(t.getRegistered()), t.getSourceBranchName(), t.getDestinationBranchName(), status(t.getStatus()),
						join(t.getItems(), i -> i.getProductName() + " x " + num(i.getQuantity()) + " " + unit(units, i.getUnit())), t.getNote(),
						t.getRequestedByName(), t.getDecidedByName(), stamp(t.getDecidedAt()), t.getDecisionNote())).toList());
	}

	private Table opnames(List<StockOpnameDTO> list, Map<String, String> units) {
		return new Table(new String[] { "Nomor", "Tanggal", "Cabang", "Status", "Barang (sistem / hitung / selisih)", "Catatan", "Diajukan Oleh", "Diputuskan Oleh", "Waktu Keputusan", "Catatan Keputusan" },
				list.stream().map(o -> row(o.getSerial(), stamp(o.getRegistered()), o.getBranchName(), status(o.getStatus()),
						join(o.getItems(), i -> i.getProductName() + ": " + num(i.getSystemQuantity()) + " / " + num(i.getCountedQuantity()) + " / "
								+ num(i.getDifference()) + " " + unit(units, i.getUnit())),
						o.getNote(), o.getRequestedByName(), o.getDecidedByName(), stamp(o.getDecidedAt()), o.getDecisionNote())).toList());
	}

	// ---------------------------------------------------------------- konfigurasi

	private Table configuration(Map<String, String> params) {
		DataResult<ConfigurationEntity> result = configurationRepository.dataTable(filter(params));
		return new Table(new String[] { "Key", "Nilai", "Diubah" },
				result.getData().stream().map(c -> row(String.valueOf(c.getId()), c.getValue(), stamp(c.getModificated()))).toList());
	}

	// ---------------------------------------------------------------- bantu

	private DataFilter filter(Map<String, String> params) {
		Map<String, String> copy = new HashMap<>(params);
		copy.remove(DataFilter.LIMIT);
		copy.remove(DataFilter.OFFSET);
		DataFilter filter = new DataFilter(copy);
		filter.setLimit(String.valueOf(Integer.MAX_VALUE));
		filter.setOffset("0");
		return filter;
	}

	private LocalDate date(Map<String, String> params, String key) {
		String value = params.get(key);
		try {
			return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
		} catch (RuntimeException e) {
			throw new ApiException("400", "Tanggal tidak valid");
		}
	}

	private Map<String, String> unitLabels() {
		try {
			return productUnitService.units().stream().collect(Collectors.toMap(u -> u.getValue(), u -> u.getLabel(), (a, b) -> a));
		} catch (RuntimeException e) {
			return new HashMap<>();
		}
	}

	private String unit(Map<String, String> units, String code) {
		return code == null ? "" : units.getOrDefault(code, code);
	}

	private String status(String value) {
		return value == null ? "" : STATUS.getOrDefault(value, value);
	}

	private String stamp(LocalDateTime value) {
		return value == null ? "" : value.format(STAMP);
	}

	private String num(BigDecimal value) {
		if (value == null) {
			return "";
		}
		BigDecimal stripped = value.stripTrailingZeros();
		return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
	}

	private <T> String join(List<T> list, Function<T, String> text) {
		return list == null ? "" : list.stream().map(text).collect(Collectors.joining("; "));
	}

	private String[] row(String... cells) {
		for (int i = 0; i < cells.length; i++) {
			if (cells[i] == null) {
				cells[i] = "";
			}
		}
		return cells;
	}

}
