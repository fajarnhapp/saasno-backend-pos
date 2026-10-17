package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum UserStatus {
    ACTIVED,
    INACTIVED,
    BLOCKED,
    SUSPEND,
    CLOSED;

    public static UserStatus fromString(String value) {
        for (UserStatus status : UserStatus.values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new ApiException("404", "Status user tidak ditemukan");
    }
}
