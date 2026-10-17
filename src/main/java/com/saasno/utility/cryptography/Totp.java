package com.saasno.utility.cryptography;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Self-contained RFC 6238 (TOTP) / RFC 4226 (HOTP) implementation — 6-digit codes, 30s step,
 * compatible with Google/Microsoft Authenticator. No external TOTP library dependency.
 */
public class Totp {

	private static final int DIGITS = 6;

	private static final int STEP_SECONDS = 30;

	private static final int WINDOW = 1;

	private static final String ALGORITHM = "HmacSHA1";

	private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

	private Totp() {
	}

	public static String generateSecret() {
		byte[] buffer = new byte[20];
		new SecureRandom().nextBytes(buffer);
		return base32Encode(buffer);
	}

	public static String buildUri(String issuer, String username, String secret) {
		String encodedIssuer = urlEncode(issuer);
		String encodedUsername = urlEncode(username);
		return "otpauth://totp/" + encodedIssuer + ":" + encodedUsername
				+ "?secret=" + secret
				+ "&issuer=" + encodedIssuer
				+ "&digits=" + DIGITS
				+ "&period=" + STEP_SECONDS;
	}

	public static boolean verify(String secret, String code) {
		if (secret == null || code == null || !code.matches("\\d{" + DIGITS + "}")) {
			return false;
		}

		long counter = Instant.now().getEpochSecond() / STEP_SECONDS;
		for (int i = -WINDOW; i <= WINDOW; i++) {
			if (generate(secret, counter + i).equals(code)) {
				return true;
			}
		}
		return false;
	}

	private static String generate(String secret, long counter) {
		byte[] key = base32Decode(secret);
		byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(key, ALGORITHM));
			byte[] hash = mac.doFinal(data);

			int offset = hash[hash.length - 1] & 0xf;
			int binary = ((hash[offset] & 0x7f) << 24)
					| ((hash[offset + 1] & 0xff) << 16)
					| ((hash[offset + 2] & 0xff) << 8)
					| (hash[offset + 3] & 0xff);

			int otp = binary % (int) Math.pow(10, DIGITS);
			return String.format("%0" + DIGITS + "d", otp);
		} catch (Exception e) {
			throw new RuntimeException("Failed to compute OTP code", e);
		}
	}

	private static String urlEncode(String value) {
		StringBuilder sb = new StringBuilder();
		for (char c : value.toCharArray()) {
			if (Character.isLetterOrDigit(c)) {
				sb.append(c);
			} else {
				sb.append(String.format("%%%02X", (int) c));
			}
		}
		return sb.toString();
	}

	private static String base32Encode(byte[] data) {
		StringBuilder sb = new StringBuilder();
		int bits = 0;
		int value = 0;
		for (byte b : data) {
			value = (value << 8) | (b & 0xff);
			bits += 8;
			while (bits >= 5) {
				sb.append(BASE32_ALPHABET[(value >>> (bits - 5)) & 0x1f]);
				bits -= 5;
			}
		}
		if (bits > 0) {
			sb.append(BASE32_ALPHABET[(value << (5 - bits)) & 0x1f]);
		}
		return sb.toString();
	}

	private static byte[] base32Decode(String data) {
		String normalized = data.trim().toUpperCase().replace("=", "");
		byte[] result = new byte[normalized.length() * 5 / 8];
		int bits = 0;
		int value = 0;
		int index = 0;
		for (char c : normalized.toCharArray()) {
			int charIndex = new String(BASE32_ALPHABET).indexOf(c);
			if (charIndex < 0) {
				continue;
			}
			value = (value << 5) | charIndex;
			bits += 5;
			if (bits >= 8) {
				result[index++] = (byte) ((value >>> (bits - 8)) & 0xff);
				bits -= 8;
			}
		}
		return result;
	}

}
