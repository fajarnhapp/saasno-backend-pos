package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum ProductStatus {
    ACTIVE,
    DISABLED;

    public static ProductStatus fromString(String value) {
        for (ProductStatus item : ProductStatus.values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        throw new ApiException("400", "Status produk tidak valid");
    }
}
