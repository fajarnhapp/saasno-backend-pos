package com.saasno.utility;

import com.saasno.utility.exception.ApiException;

// Nomor WhatsApp dalam format internasional tanpa tanda: spasi, strip, kurung, dan + dibuang, awalan 0 menjadi 62, 8-15 digit.
public final class WhatsappNumber {

	private WhatsappNumber() {
	}

	public static String normalize(String raw) {
		String digits = raw == null ? "" : raw.replaceAll("[\\s\\-().+]", "");
		if (digits.startsWith("0")) {
			digits = "62" + digits.substring(1);
		}
		if (!digits.matches("[1-9][0-9]{7,14}")) {
			throw new ApiException("400", "Nomor WhatsApp harus berupa nomor telepon 8-15 digit (contoh 6281234567890)");
		}
		return digits;
	}

}
