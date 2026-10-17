package com.saasno.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.sale.SaleChargeDTO;
import com.saasno.utility.exception.ApiException;

// Biaya lain-lain pada penjualan (pajak, biaya layanan, kemasan, ...). Daftarnya ada di konfigurasi SALE_CHARGE:
// [{"value":"PPN","label":"PPN","type":"PERCENT","amount":11,"inclusive":false}, ...] dan dihitung satu per satu.
// Semua dihitung dari nilai akhir (setelah promo dan diskon), tidak bertumpuk. PERCENT boleh sudah termasuk harga
// (inclusive); FIXED (rupiah per transaksi) selalu ditambahkan.
@Service
public class SaleChargeService {

	public static final String PERCENT = "PERCENT";
	public static final String FIXED = "FIXED";
	private static final int MAX_CHARGES = 10;
	private static final BigDecimal HUNDRED = new BigDecimal("100");
	private static final Pattern CODE = Pattern.compile("^[A-Z0-9_]{1,32}$");

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Charge(String value, String label, String type, BigDecimal amount, Boolean inclusive) {
		boolean isInclusive() {
			return Boolean.TRUE.equals(inclusive);
		}
	}

	// Hasil hitung untuk satu nilai akhir.
	public record Result(List<SaleChargeDTO> charges, BigDecimal total, BigDecimal exclusive) {
	}

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private ObjectMapper objectMapper;

	public List<Charge> active() {
		String raw = configurationRepository.get(ConfigurationKey.SALE_CHARGE);
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(raw, new TypeReference<List<Charge>>() {
			});
		} catch (JsonProcessingException e) {
			return List.of();
		}
	}

	// Menghitung semua biaya di atas nilai akhir `net` (rupiah utuh). total = jumlah semua biaya (termasuk yang sudah ada di
	// harga); exclusive = yang ditambahkan ke tagihan.
	public Result compute(BigDecimal net) {
		List<Charge> list = active();
		BigDecimal inclusivePercent = BigDecimal.ZERO;
		for (Charge charge : list) {
			if (PERCENT.equals(charge.type()) && charge.isInclusive()) {
				inclusivePercent = inclusivePercent.add(charge.amount());
			}
		}
		List<SaleChargeDTO> applied = new ArrayList<>();
		BigDecimal total = BigDecimal.ZERO;
		BigDecimal exclusive = BigDecimal.ZERO;
		for (Charge charge : list) {
			BigDecimal amount;
			BigDecimal rate = null;
			boolean inclusive = false;
			if (PERCENT.equals(charge.type())) {
				rate = charge.amount();
				inclusive = charge.isInclusive();
				amount = inclusive
						? net.multiply(rate).divide(HUNDRED.add(inclusivePercent), 0, RoundingMode.HALF_UP)
						: net.multiply(rate).divide(HUNDRED, 0, RoundingMode.HALF_UP);
			} else {
				amount = charge.amount().setScale(0, RoundingMode.HALF_UP);
			}
			if (net.signum() == 0) {
				amount = BigDecimal.ZERO;
			}
			amount = amount.setScale(2);
			applied.add(new SaleChargeDTO(charge.value(), charge.label(), charge.type(), rate, inclusive, amount, net.setScale(2)));
			total = total.add(amount);
			if (!inclusive) {
				exclusive = exclusive.add(amount);
			}
		}
		return new Result(applied, total.setScale(2), exclusive.setScale(2));
	}

	// Biaya yang ditambahkan ke tagihan bila nilai akhir tersisa `net` (untuk retur), dari biaya yang dibekukan pada
	// transaksi. PERCENT mengikuti tarifnya; FIXED mengecil sebanding dengan nilai akhir yang tersisa.
	public BigDecimal exclusiveFor(List<SaleChargeDTO> frozen, BigDecimal net) {
		BigDecimal sum = BigDecimal.ZERO;
		for (SaleChargeDTO charge : frozen) {
			if (charge.inclusive()) {
				continue;
			}
			if (PERCENT.equals(charge.type())) {
				sum = sum.add(net.multiply(charge.rate()).divide(HUNDRED, 0, RoundingMode.HALF_UP));
			} else if (charge.base() != null && charge.base().signum() > 0) {
				sum = sum.add(charge.amount().multiply(net).divide(charge.base(), 0, RoundingMode.HALF_UP));
			}
		}
		return sum;
	}

	public String write(List<SaleChargeDTO> charges) {
		try {
			return objectMapper.writeValueAsString(charges);
		} catch (JsonProcessingException e) {
			throw new ApiException("500", "Biaya lain-lain tidak dapat disimpan");
		}
	}

	public List<SaleChargeDTO> read(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<List<SaleChargeDTO>>() {
			});
		} catch (JsonProcessingException e) {
			return List.of();
		}
	}

	// Dipakai ConfigurationService. Boleh kosong (tanpa biaya).
	public String validate(String raw) {
		List<Charge> list;
		try {
			list = objectMapper.readValue(raw == null ? "" : raw, new TypeReference<List<Charge>>() {
			});
		} catch (JsonProcessingException e) {
			throw new ApiException("400", "SALE_CHARGE harus berupa array JSON {value, label, type, amount, inclusive}");
		}
		if (list == null) {
			throw new ApiException("400", "SALE_CHARGE harus berupa array JSON");
		}
		if (list.size() > MAX_CHARGES) {
			throw new ApiException("400", "Biaya lain-lain maksimal " + MAX_CHARGES + " item");
		}
		Set<String> seen = new HashSet<>();
		List<Charge> clean = new ArrayList<>();
		for (Charge charge : list) {
			if (charge == null || charge.value() == null || !CODE.matcher(charge.value()).matches()) {
				throw new ApiException("400", "Kode biaya hanya boleh A-Z, 0-9, atau _ (maksimal 32 karakter)");
			}
			if (charge.label() == null || charge.label().isBlank() || charge.label().length() > 64) {
				throw new ApiException("400", "Label biaya tidak boleh kosong (maksimal 64 karakter)");
			}
			if (!seen.add(charge.value())) {
				throw new ApiException("400", "Biaya tidak boleh ganda: " + charge.value());
			}
			if (!PERCENT.equals(charge.type()) && !FIXED.equals(charge.type())) {
				throw new ApiException("400", "Jenis biaya harus PERCENT atau FIXED (" + charge.value() + ")");
			}
			BigDecimal amount = charge.amount();
			if (amount == null || amount.signum() <= 0) {
				throw new ApiException("400", "Nilai biaya harus lebih dari 0 (" + charge.value() + ")");
			}
			if (PERCENT.equals(charge.type())) {
				if (amount.compareTo(HUNDRED) > 0 || amount.stripTrailingZeros().scale() > 2) {
					throw new ApiException("400", "Persentase biaya harus 0-100 dengan maksimal 2 desimal (" + charge.value() + ")");
				}
			} else if (amount.stripTrailingZeros().scale() > 0 || amount.compareTo(new BigDecimal("1000000000")) > 0) {
				throw new ApiException("400", "Nominal biaya harus rupiah utuh maksimal 1.000.000.000 (" + charge.value() + ")");
			}
			boolean inclusive = Boolean.TRUE.equals(charge.inclusive());
			if (FIXED.equals(charge.type()) && inclusive) {
				throw new ApiException("400", "Biaya nominal tetap tidak bisa 'sudah termasuk harga' (" + charge.value() + ")");
			}
			clean.add(new Charge(charge.value(), charge.label().trim(), charge.type(), amount.stripTrailingZeros().scale() < 0
					? amount.setScale(0) : amount, inclusive));
		}
		BigDecimal inclusiveSum = BigDecimal.ZERO;
		for (Charge charge : clean) {
			if (PERCENT.equals(charge.type()) && charge.isInclusive()) {
				inclusiveSum = inclusiveSum.add(charge.amount());
			}
		}
		if (inclusiveSum.compareTo(HUNDRED) >= 0) {
			throw new ApiException("400", "Jumlah persentase biaya yang sudah termasuk harga harus di bawah 100");
		}
		try {
			return objectMapper.writeValueAsString(clean);
		} catch (JsonProcessingException e) {
			throw new ApiException("500", "SALE_CHARGE tidak dapat disimpan");
		}
	}

}
