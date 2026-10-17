package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum PromoStatus {
    ACTIVE,
    DISABLED;

    public static PromoStatus fromString(String value) {
        for (PromoStatus item : PromoStatus.values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        throw new ApiException("400", "Status promo tidak valid");
    }
}
