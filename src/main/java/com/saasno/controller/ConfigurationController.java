package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.ConfigurationService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.configuration.ConfigurationDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(Path.API_CONFIGURATION)
public class ConfigurationController {

    @Autowired
    private ConfigurationService configurationService;

    @PostMapping
    public ResponseModel<ConfigurationDTO> create(@Valid @RequestBody ConfigurationDTO configuration) {
        return configurationService.create(configuration);
    }

    @GetMapping
    public ResponseEntity<DataResult<ConfigurationDTO>> read(HttpServletRequest request) {
        return configurationService.read(request);
    }

    @GetMapping("/{id}")
    public ResponseModel<ConfigurationDTO> find(@PathVariable String id) {
        return configurationService.find(id);
    }

    @PutMapping
    public ResponseModel<ConfigurationDTO> update(@Valid @RequestBody ConfigurationDTO configuration) {
        return configurationService.update(configuration);
    }

    @DeleteMapping("/{id}")
    public ResponseModel<Void> remove(@PathVariable String id) {
        return configurationService.remove(id);
    }
}
