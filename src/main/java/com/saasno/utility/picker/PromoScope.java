package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum PromoScope {
    PRODUCT,
    TOTAL;

    public static PromoScope fromString(String value) {
        for (PromoScope item : PromoScope.values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        throw new ApiException("400", "Cakupan promo tidak valid");
    }
}
