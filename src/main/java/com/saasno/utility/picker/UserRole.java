package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum UserRole {
    OWNER,
    STAFF_LAPANGAN,
    STAFF_PROCUREMENT,
    STAFF_CHECKER;

    public static UserRole fromString(String value) {
        for (UserRole status : UserRole.values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new ApiException("404", "Role user tidak ditemukan");
    }
}
