package com.saasno.utility.dto.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Pengaturan katalog satu cabang. Permintaan memakai branchId (user global), slug, whatsapp, dan enabled; branchName diisi sistem.
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogSettingDTO(String branchId, String branchName, String slug, String whatsapp, Boolean enabled) {
}
