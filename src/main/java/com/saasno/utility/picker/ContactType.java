package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum ContactType {
    PHONE,
    EMERGENCY_PHONE,
    EMAIL,
    WHATSAPP,
    TELEGRAM,
    LINKEDIN,
    INSTAGRAM,
    FACEBOOK,
    TWITTER,
    YOUTUBE;

    public static ContactType fromString(String value) {
        for (ContactType item : ContactType.values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        throw new ApiException("400", "Jenis kontak tidak valid");
    }
}
