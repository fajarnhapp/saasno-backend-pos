package com.saasno.utility.verification;

import java.util.List;

public class ListVerification {

	public static boolean exist(List<?> data) {
		if (data == null || data.size() <= 0) {
			return false;
		}

		return true;
	}

	public static boolean one(List<?> data) {
		if (!exist(data) || data.size() != 1) {
			return false;
		}

		return true;
	}

}
