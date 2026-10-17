package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum CustomerType {
    INDIVIDU,
    BISNIS;

    public static CustomerType fromString(String value) {
        for (CustomerType item : CustomerType.values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        throw new ApiException("400", "Jenis customer tidak valid");
    }
}
