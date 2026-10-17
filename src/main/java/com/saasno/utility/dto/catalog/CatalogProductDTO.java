package com.saasno.utility.dto.catalog;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Produk milik cabang beserta data katalognya. Permintaan ubah memakai id, visible (false = disembunyikan dari katalog), dan description;
// sisanya diisi sistem. inStock: produk ada stoknya di cabang ini, syarat agar tampil di katalog.
// imageVersion (epoch milidetik) berubah tiap foto diganti, dipakai untuk menghindari foto lama di cache.
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogProductDTO(String id, String serial, String name, BigDecimal price, String unit, String status,
		Boolean visible, String description, Boolean hasImage, Long imageVersion, Boolean inStock) {
}
