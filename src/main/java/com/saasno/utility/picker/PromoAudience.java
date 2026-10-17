package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum PromoAudience {
    ALL,
    TYPE,
    CUSTOMER;

    public static PromoAudience fromString(String value) {
        for (PromoAudience item : PromoAudience.values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        throw new ApiException("400", "Sasaran promo tidak valid");
    }
}
