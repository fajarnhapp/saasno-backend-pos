package com.saasno.utility.dto.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Field yang diisi sistem (read-only, diabaikan saat create/update): id, serial, modificated,
// registered. Sisanya (name, price, unit) diisi user.
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductDTO {

	private String id;
	private String branchId;
	private String branchName;
	// Diisi sistem: true kalau produk milik cabang induk (hanya bisa dibaca oleh cabang ini).
	private Boolean inherited;
	private String serial;

	@NotBlank(message = "Nama tidak boleh kosong")
	private String name;

	@NotNull(message = "Harga tidak boleh kosong")
	private BigDecimal price;
	// Harga pokok (opsional), untuk laba kotor; kosong = belum diketahui.
	private BigDecimal costPrice;

	@NotBlank(message = "Satuan tidak boleh kosong")
	private String unit;

	// Keterangan produk (maks 1000 karakter, tampil di katalog publik). Saat update: null = tidak berubah, teks kosong = dihapus.
	private String description;

	// Lacak batch dan tanggal kedaluwarsa. Saat update: null = tidak berubah.
	private Boolean trackExpiry;

	// Diisi sistem (read-only): waktu foto katalog terakhir diganti (epoch milidetik); kosong = belum ada foto.
	private Long imageVersion;

	// ACTIVE | DISABLED; kosong saat create = ACTIVE, null saat update = tidak berubah.
	private String status;

	// null saat update = tidak berubah; list (boleh kosong) = menggantikan seluruh konversi.
	private List<ProductUnitConversionDTO> conversions;

	// Harga bertingkat; semantik null/list sama seperti conversions.
	private List<ProductPriceTierDTO> priceTiers;

	// Harga khusus per tipe customer / customer tertentu; semantik null/list sama seperti conversions.
	private List<ProductCustomerPriceDTO> customerPrices;

	private LocalDateTime modificated;
	private LocalDateTime registered;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSerial() {
		return serial;
	}

	public void setSerial(String serial) {
		this.serial = serial;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public List<ProductUnitConversionDTO> getConversions() {
		return conversions;
	}

	public void setConversions(List<ProductUnitConversionDTO> conversions) {
		this.conversions = conversions;
	}

	public List<ProductCustomerPriceDTO> getCustomerPrices() {
		return customerPrices;
	}

	public void setCustomerPrices(List<ProductCustomerPriceDTO> customerPrices) {
		this.customerPrices = customerPrices;
	}

	public List<ProductPriceTierDTO> getPriceTiers() {
		return priceTiers;
	}

	public void setPriceTiers(List<ProductPriceTierDTO> priceTiers) {
		this.priceTiers = priceTiers;
	}

	public LocalDateTime getModificated() {
		return modificated;
	}

	public void setModificated(LocalDateTime modificated) {
		this.modificated = modificated;
	}

	public LocalDateTime getRegistered() {
		return registered;
	}

	public void setRegistered(LocalDateTime registered) {
		this.registered = registered;
	}

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

	public String getBranchName() {
		return branchName;
	}

	public void setBranchName(String branchName) {
		this.branchName = branchName;
	}

	public Boolean getInherited() {
		return inherited;
	}

	public void setInherited(Boolean inherited) {
		this.inherited = inherited;
	}

	public Long getImageVersion() {
		return imageVersion;
	}

	public void setImageVersion(Long imageVersion) {
		this.imageVersion = imageVersion;
	}

	public Boolean getTrackExpiry() {
		return trackExpiry;
	}

	public void setTrackExpiry(Boolean trackExpiry) {
		this.trackExpiry = trackExpiry;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public BigDecimal getCostPrice() {
		return costPrice;
	}

	public void setCostPrice(BigDecimal costPrice) {
		this.costPrice = costPrice;
	}

}
