package com.saasno.service;

import java.math.BigInteger;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.ConfigurationEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.configuration.ConfigurationDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.mapper.ConfigurationMapper;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class ConfigurationService {

    // Konfigurasi yang nilainya bilangan bulat (bukan rupiah): dipakai untuk pesan galat yang tepat.
    private static final java.util.Set<String> WHOLE_NUMBER_KEYS = java.util.Set.of(
            ConfigurationKey.LOW_STOCK_THRESHOLD.name(), ConfigurationKey.NOTIFICATION_RETENTION_DAYS.name(),
            ConfigurationKey.HOLD_STALE_HOURS.name(), ConfigurationKey.MAX_LOGIN_ATTEMPTS.name(),
            ConfigurationKey.LOGIN_LOCK_MINUTES.name());

    // Yang nilainya minimal 1 (lama simpan dan jam tidak boleh 0).
    private static final java.util.Set<String> AT_LEAST_ONE_KEYS = java.util.Set.of(
            ConfigurationKey.NOTIFICATION_RETENTION_DAYS.name(), ConfigurationKey.HOLD_STALE_HOURS.name(),
            ConfigurationKey.MAX_LOGIN_ATTEMPTS.name(), ConfigurationKey.LOGIN_LOCK_MINUTES.name());

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private ConfigurationMapper configurationMapper;

    @Autowired
    private ProductUnitService productUnitService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private SaleChargeService saleChargeService;

    @Transactional
    public ResponseModel<ConfigurationDTO> create(ConfigurationDTO configuration) {
        configuration.setValue(normalize(configuration));
        configurationRepository.store(configurationMapper.entity(configuration));
        return new ResponseModel<ConfigurationDTO>().success(configuration);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<DataResult<ConfigurationDTO>> read(HttpServletRequest request) {
        try {
            DataResult<ConfigurationEntity> result = configurationRepository.dataTable(new DataFilter(request));
            List<ConfigurationDTO> dto = configurationMapper.dto(result.getData());
            return new ResponseEntity<DataResult<ConfigurationDTO>>(
                    new DataResult<>(dto, result.getTotal(), result.getGrandTotal()), HttpStatus.OK);
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
            return new ResponseEntity<DataResult<ConfigurationDTO>>(
                    new DataResult<ConfigurationDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
        }
    }

    @Transactional(readOnly = true)
    public ResponseModel<ConfigurationDTO> find(String id) {
        ConfigurationEntity existingConfiguration = configurationRepository.find(id);
        if (existingConfiguration == null) {
            throw new ApiException("404", "Data konfigurasi tidak ditemukan");
        }

        return new ResponseModel<ConfigurationDTO>().success(configurationMapper.dto(existingConfiguration));
    }

    @Transactional
    public ResponseModel<ConfigurationDTO> update(ConfigurationDTO configuration) {
        ConfigurationEntity existingConfiguration = configurationRepository.find(configuration.getId().name());
        if (existingConfiguration == null) {
            throw new ApiException("404", "Konfigurasi tidak ditemukan");
        }

        configuration.setValue(normalize(configuration));
        existingConfiguration.setValue(configuration.getValue());
        configurationRepository.store(existingConfiguration);
        return new ResponseModel<ConfigurationDTO>().success(configuration);
    }

    @Transactional
    public ResponseModel<Void> remove(String id) {
        ConfigurationEntity existingConfiguration = configurationRepository.find(id);
        if (existingConfiguration == null) {
            throw new ApiException("404", "Konfigurasi tidak ditemukan");
        }

        configurationRepository.delete(existingConfiguration);
        return new ResponseModel<Void>().success(null);
    }

    // Konfigurasi bertipe terstruktur divalidasi sebelum disimpan.
    private String normalize(ConfigurationDTO configuration) {
        if (configuration.getId() == ConfigurationKey.PRODUCT_UNIT) {
            return productUnitService.validate(configuration.getValue());
        }
        ConfigurationKey id = configuration.getId();
        if (id == ConfigurationKey.EXPENSE_CATEGORY) {
            return expenseService.validateCategories(configuration.getValue());
        }
        if (id == ConfigurationKey.EXPENSE_WARNING_AMOUNT || id == ConfigurationKey.LOW_STOCK_THRESHOLD
                || id == ConfigurationKey.NOTIFICATION_RETENTION_DAYS || id == ConfigurationKey.HOLD_STALE_HOURS
                || id == ConfigurationKey.MAX_LOGIN_ATTEMPTS || id == ConfigurationKey.LOGIN_LOCK_MINUTES) {
            return amount(configuration.getValue(), id.name());
        }
        if (id == ConfigurationKey.MANUAL_DISCOUNT_MAX_PERCENT) {
            return percent(configuration.getValue(), id.name());
        }
        if (id == ConfigurationKey.ADMIN_WHATSAPP) {
            return whatsapp(configuration.getValue());
        }
        if (id == ConfigurationKey.SALE_CHARGE) {
            return saleChargeService.validate(configuration.getValue());
        }
        return configuration.getValue();
    }

    // Nomor WhatsApp: spasi, strip, kurung, dan + dibuang; awalan 0 menjadi 62; 8-15 digit.
    private String whatsapp(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[\\s\\-().+]", "");
        if (digits.startsWith("0")) {
            digits = "62" + digits.substring(1);
        }
        if (!digits.matches("[1-9][0-9]{7,14}")) {
            throw new ApiException("400", "ADMIN_WHATSAPP harus berupa nomor telepon 8-15 digit (contoh 6281234567890)");
        }
        return digits;
    }

    // Rupiah utuh >= 0.
    private String amount(String raw, String name) {
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(raw == null ? "" : raw.trim());
            if (value.signum() < 0 || value.stripTrailingZeros().scale() > 0
                    || (AT_LEAST_ONE_KEYS.contains(name) && value.signum() == 0)
                    || (ConfigurationKey.NOTIFICATION_RETENTION_DAYS.name().equals(name) && value.compareTo(new java.math.BigDecimal(30)) > 0)) {
                throw new NumberFormatException();
            }
            return value.toBigInteger().toString();
        } catch (NumberFormatException e) {
            if (ConfigurationKey.NOTIFICATION_RETENTION_DAYS.name().equals(name)) {
                throw new ApiException("400", name + " harus berupa bilangan bulat 1 sampai 30 (notifikasi hanya disimpan sebulan)");
            }
            String minimum = AT_LEAST_ONE_KEYS.contains(name) ? "1" : "0";
            throw new ApiException("400", name + (WHOLE_NUMBER_KEYS.contains(name)
                    ? " harus berupa bilangan bulat, " + minimum + " atau lebih"
                    : " harus berupa rupiah bulat, 0 atau lebih"));
        }
    }

    // Persen 0..100 dengan maksimal 4 desimal.
    private String percent(String raw, String name) {
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(raw == null ? "" : raw.trim());
            if (value.signum() < 0 || value.compareTo(new java.math.BigDecimal("100")) > 0
                    || value.stripTrailingZeros().scale() > 4) {
                throw new NumberFormatException();
            }
            return value.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            throw new ApiException("400", name + " harus berupa angka antara 0 dan 100");
        }
    }
}
