package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum CustomerStatus {
    ACTIVE,
    DISABLED;

    public static CustomerStatus fromString(String value) {
        for (CustomerStatus item : CustomerStatus.values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        throw new ApiException("400", "Status customer tidak valid");
    }
}
