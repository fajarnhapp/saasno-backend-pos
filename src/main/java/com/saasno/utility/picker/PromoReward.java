package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum PromoReward {
    DISCOUNT_FIXED,
    DISCOUNT_PERCENT,
    FREE_PRODUCT;

    public static PromoReward fromString(String value) {
        for (PromoReward item : PromoReward.values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        throw new ApiException("400", "Hadiah promo tidak valid");
    }
}
