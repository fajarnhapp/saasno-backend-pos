package com.saasno.utility.verification;

import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberVerification {

	public static boolean nullOrZero(Integer data) {
		if (data != null && data > 0) {
			return false;
		}

		return true;
	}

	public static boolean nullOrZero(Long data) {
		if (data != null && data > 0) {
			return false;
		}

		return true;
	}

	public static boolean nullOrZero(BigInteger data) {
		if (data != null && data.compareTo(BigInteger.ZERO) <= 0) {
			return true;
		}

		return false;
	}

	public static boolean nullOrZero(BigDecimal data) {
		if (data != null && data.compareTo(BigDecimal.ZERO) <= 0) {
			return true;
		}

		return false;
	}

	public static boolean isBigDecimal(String data) {
		try {
			new BigDecimal(data);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static boolean isBigInteger(String data) {
		try {
			new BigInteger(data);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

}
