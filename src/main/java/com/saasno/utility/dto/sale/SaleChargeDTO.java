package com.saasno.utility.dto.sale;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Satu biaya lain-lain (pajak, biaya layanan, dll.) yang dibekukan pada transaksi.
// type PERCENT: rate = persen dari nilai akhir; type FIXED: rate tidak dipakai. base = nilai akhir saat transaksi.
@JsonIgnoreProperties(ignoreUnknown = true)
public record SaleChargeDTO(String code, String label, String type, BigDecimal rate, boolean inclusive, BigDecimal amount,
		BigDecimal base) {
}
