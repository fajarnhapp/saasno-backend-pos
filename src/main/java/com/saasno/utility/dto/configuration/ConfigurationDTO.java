package com.saasno.utility.dto.configuration;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.saasno.utility.constant.ConfigurationKey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigurationDTO {

    @NotNull(message = "ID konfigurasi tidak boleh kosong")
    private ConfigurationKey id;

    @NotBlank(message = "Nilai tidak boleh kosong")
    private String value;

    private LocalDateTime modificated;

    private LocalDateTime registered;

    public ConfigurationKey getId() {
        return id;
    }

    public void setId(ConfigurationKey id) {
        this.id = id;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
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
}
