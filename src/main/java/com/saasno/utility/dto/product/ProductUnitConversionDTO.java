package com.saasno.utility.dto.product;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Arti: 1 {toUnit} = {value} {fromUnit}. fromUnit diisi sistem (selalu satuan produk),
// diabaikan saat create/update. toUnit dan value diisi user.
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductUnitConversionDTO {

	private String fromUnit;
	private String toUnit;
	private BigDecimal value;

	public String getFromUnit() {
		return fromUnit;
	}

	public void setFromUnit(String fromUnit) {
		this.fromUnit = fromUnit;
	}

	public String getToUnit() {
		return toUnit;
	}

	public void setToUnit(String toUnit) {
		this.toUnit = toUnit;
	}

	public BigDecimal getValue() {
		return value;
	}

	public void setValue(BigDecimal value) {
		this.value = value;
	}

}
