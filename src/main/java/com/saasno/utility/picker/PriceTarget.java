package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum PriceTarget {
    TYPE,
    CUSTOMER;

    public static PriceTarget fromString(String value) {
        for (PriceTarget item : PriceTarget.values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        throw new ApiException("400", "Sasaran harga tidak valid");
    }
}
