package com.saasno.service;

import java.math.BigInteger;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.saasno.entity.LogEntity;
import com.saasno.repository.LogRepository;
import com.saasno.utility.dto.log.LogDTO;
import com.saasno.utility.logger.Log;
import com.saasno.utility.mapper.LogMapper;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class LogService {

    private final LogRepository logRepository;
    private final LogMapper logMapper;

    public LogService(LogRepository logRepository, LogMapper logMapper) {
        this.logRepository = logRepository;
        this.logMapper = logMapper;
    }

    public void save(LogEntity entity) {
        logRepository.store(entity);
    }

    public ResponseEntity<DataResult<LogDTO>> read(HttpServletRequest request) {
        try {
            DataResult<LogEntity> result = logRepository.dataTable(new DataFilter(request));
            List<LogDTO> dto = logMapper.dto(result.getData());
            return new ResponseEntity<DataResult<LogDTO>>(
                    new DataResult<>(dto, result.getTotal(), result.getGrandTotal()), HttpStatus.OK);
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
            return new ResponseEntity<DataResult<LogDTO>>(new DataResult<LogDTO>(BigInteger.ZERO, BigInteger.ZERO),
                    HttpStatus.OK);
        }
    }
}
