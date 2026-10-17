package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum PromoCondition {
    QUANTITY,
    AMOUNT;

    public static PromoCondition fromString(String value) {
        for (PromoCondition item : PromoCondition.values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        throw new ApiException("400", "Syarat promo tidak valid");
    }
}
