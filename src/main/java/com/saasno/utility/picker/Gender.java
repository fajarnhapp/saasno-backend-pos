package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum Gender {
    MALE,
    FEMALE;

    public static Gender fromString(String value) {
        for (Gender item : Gender.values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        throw new ApiException("400", "Jenis kelamin tidak valid");
    }
}
