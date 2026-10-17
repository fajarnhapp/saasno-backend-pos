package com.saasno.utility.verification;

public class StringVerification {

	public static boolean nullOrEmpty(String data) {
		if (data != null && !data.isEmpty()) {
			return false;
		}
		return true;
	}

}
