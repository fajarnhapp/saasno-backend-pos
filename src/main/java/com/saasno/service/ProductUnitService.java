package com.saasno.service;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.repository.ProductRepository;
import com.saasno.repository.ProductUnitConversionRepository;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.product.ProductUnitDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;

// Daftar satuan produk disimpan di configuration (PRODUCT_UNIT) supaya bisa diubah tanpa deploy.
@Service
public class ProductUnitService {

	private static final Pattern CODE = Pattern.compile("^[A-Z0-9_]{1,32}$");
	private static final int MAX_LABEL = 64;
	private static final int MAX_UNITS = 200;
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductUnitConversionRepository conversionRepository;

	@Transactional(readOnly = true)
	public ResponseModel<List<ProductUnitDTO>> read() {
		return new ResponseModel<List<ProductUnitDTO>>().success(units());
	}

	@Transactional(readOnly = true)
	public List<ProductUnitDTO> units() {
		String value = configurationRepository.get(ConfigurationKey.PRODUCT_UNIT);
		if (value == null || value.isBlank()) {
			throw new ApiException("500", "Konfigurasi satuan produk belum diatur");
		}
		return parse(value);
	}

	// Kode satuan harus ada di daftar konfigurasi.
	@Transactional(readOnly = true)
	public void assertExists(String code) {
		if (units().stream().noneMatch(u -> u.getValue().equals(code))) {
			throw new ApiException("400", "Satuan belum terdaftar: " + code);
		}
	}

	// Memvalidasi nilai konfigurasi baru dan mengembalikan JSON yang sudah dirapikan. Satuan yang
	// dibuang dari daftar tidak boleh masih dipakai produk atau konversi.
	@Transactional(readOnly = true)
	public String validate(String value) {
		List<ProductUnitDTO> items = parse(value);

		String previous = configurationRepository.get(ConfigurationKey.PRODUCT_UNIT);
		if (previous != null && !previous.isBlank()) {
			Set<String> removed = parse(previous).stream().map(ProductUnitDTO::getValue)
					.collect(Collectors.toCollection(LinkedHashSet::new));
			items.forEach(i -> removed.remove(i.getValue()));
			if (!removed.isEmpty()) {
				Set<String> used = new HashSet<>(productRepository.findUsedUnits());
				used.addAll(conversionRepository.findUsedUnits());
				removed.retainAll(used);
				if (!removed.isEmpty()) {
					throw new ApiException("409", "Satuan masih dipakai oleh produk: " + String.join(", ", removed));
				}
			}
		}

		try {
			return MAPPER.writeValueAsString(items);
		} catch (Exception e) {
			throw new ApiException("400", "Daftar satuan produk tidak valid");
		}
	}

	private List<ProductUnitDTO> parse(String value) {
		List<ProductUnitDTO> items;
		try {
			items = MAPPER.readValue(value, new TypeReference<List<ProductUnitDTO>>() {
			});
		} catch (Exception e) {
			throw new ApiException("400", "Daftar satuan produk harus berupa array JSON {value, label}");
		}
		if (items == null || items.isEmpty()) {
			throw new ApiException("400", "Daftar satuan produk tidak boleh kosong");
		}
		if (items.size() > MAX_UNITS) {
			throw new ApiException("400", "Daftar satuan produk tidak boleh lebih dari " + MAX_UNITS + " item");
		}

		Set<String> seen = new HashSet<>();
		for (ProductUnitDTO item : items) {
			if (item == null) {
				throw new ApiException("400", "Satuan produk tidak valid");
			}
			String code = item.getValue() == null ? null : item.getValue().trim();
			String label = item.getLabel() == null ? null : item.getLabel().trim();
			if (code == null || !CODE.matcher(code).matches()) {
				throw new ApiException("400", "Kode satuan tidak valid (gunakan A-Z, 0-9, _; maksimal 32): " + item.getValue());
			}
			if (label == null || label.isEmpty() || label.length() > MAX_LABEL) {
				throw new ApiException("400", "Label satuan harus 1-" + MAX_LABEL + " karakter: " + code);
			}
			if (!seen.add(code)) {
				throw new ApiException("400", "Kode satuan tidak boleh ganda: " + code);
			}
			item.setValue(code);
			item.setLabel(label);
		}
		return items;
	}

}
