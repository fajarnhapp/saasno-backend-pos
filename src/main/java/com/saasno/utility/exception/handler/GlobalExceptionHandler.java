package com.saasno.utility.exception.handler;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ResponseModel<String>> handleApiException(ApiException ex) {
        return ResponseEntity
                .status(Integer.parseInt(ex.getCode()))
                .body(new ResponseModel<String>(ex.getMessage()));
    }

    @ExceptionHandler({ MaxUploadSizeExceededException.class, MultipartException.class })
    public ResponseEntity<ResponseModel<String>> handleUpload(Exception ex) {
        boolean tooLarge = ex instanceof MaxUploadSizeExceededException;
        return ResponseEntity
                .status(tooLarge ? 413 : 400)
                .body(new ResponseModel<>(tooLarge ? "Ukuran berkas melebihi batas 5 MB" : "Unggahan tidak valid"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseModel<String>> handleEnumParseError(HttpMessageNotReadableException ex) {
        String rawMessage = ex.getMessage();
        String fieldName = "Field";
        int start = rawMessage.indexOf("`");
        int end = rawMessage.indexOf("`", start + 1);
        if (start != -1 && end != -1) {
            String fullClassName = rawMessage.substring(start + 1, end);
            fieldName = fullClassName.substring(fullClassName.lastIndexOf(".") + 1);
        }

        String message = fieldName + " must not be empty or invalid";
        return ResponseEntity
                .status(400)
                .body(new ResponseModel<>(message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseModel<String>> handleValidationException(MethodArgumentNotValidException ex) {
        FieldError error = ex.getBindingResult().getFieldErrors().get(0);
        String message = error.getDefaultMessage();
        return ResponseEntity
                .status(400)
                .body(new ResponseModel<>(message));
    }

    @ExceptionHandler(InvalidFormatException.class)
    public ResponseEntity<ResponseModel<String>> handleInvalidFormatException(InvalidFormatException ex) {

        if (ex.getTargetType() != null && ex.getTargetType().isEnum()) {
            String fieldName = ex.getPath().get(0).getFieldName();
            return ResponseEntity
                    .status(400)
                    .body(new ResponseModel<String>(fieldName + " tidak valid"));
        }

        return ResponseEntity
                .status(400)
                .body(new ResponseModel<String>("Format permintaan tidak valid"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResponseModel<String>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations()
                .stream()
                .map(cv -> cv.getMessage())
                .findFirst()
                .orElse("Kesalahan validasi");

        return ResponseEntity
                .status(400)
                .body(new ResponseModel<>(message));
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ResponseModel<String>> handleTransactionSystem(TransactionSystemException ex) {
        Throwable cause = ex.getRootCause();
        if (cause instanceof ConstraintViolationException cve) {
            String message = cve.getConstraintViolations()
                    .stream()
                    .map(cv -> cv.getMessage())
                    .findFirst()
                    .orElse("Kesalahan validasi");

            return ResponseEntity
                    .status(400)
                    .body(new ResponseModel<>(message));
        }
        return ResponseEntity
                .status(500)
                .body(new ResponseModel<>(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseModel<String>> handleException(Exception ex) {
        Log.error(ex.getMessage(), ex);
        return ResponseEntity
                .status(500)
                .body(new ResponseModel<String>("Permintaan tidak dapat diproses, silakan coba lagi sebentar lagi."));
    }

}
