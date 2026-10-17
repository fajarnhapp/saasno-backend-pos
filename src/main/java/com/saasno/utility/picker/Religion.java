package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum Religion {
    ISLAM,
    KATOLIK,
    PROTESTAN,
    HINDU,
    BUDHA,
    KONGHUCU;

    public static Religion fromString(String value) {
        for (Religion item : Religion.values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        throw new ApiException("400", "Agama tidak valid");
    }
}
