package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum DownloadJobStatus {
    QUEUED,
    PROCESSING,
    DONE,
    FAILED;

    public static DownloadJobStatus fromString(String value) {
        for (DownloadJobStatus status : DownloadJobStatus.values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new ApiException("404", "Status unduhan tidak ditemukan");
    }
}
