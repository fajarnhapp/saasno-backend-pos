package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum AddressType {
    HOME,
    OFFICE,
    CARD,
    BILLING,
    SHIPPING,
    DOMICILE;

    public static AddressType fromString(String value) {
        for (AddressType item : AddressType.values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        throw new ApiException("400", "Jenis alamat tidak valid");
    }
}
