package com.saasno.utility;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasno.utility.logger.Log;

@Component
public class ConverterUtility {

    private static final String ERROR = "Converter error : ";

    private static ObjectMapper mapper;

    @Autowired
    public ConverterUtility(ObjectMapper objectMapper) {
        mapper = objectMapper;
    }

    public static String toString(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
            return ERROR + e.getMessage();
        }
    }

    public static String toPrettyString(Object object) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
            return ERROR + e.getMessage();
        }
    }

    public static <T> T readValue(Object object, Class<T> clazz) {
        try {
            return mapper.readValue(mapper.writeValueAsString(object), clazz);
        } catch (Exception e) {
            throw new RuntimeException(ERROR, e);
        }
    }

    public static String toCurrency(BigDecimal value) {
        if (value == null)
            return "";

        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.of("id", "ID"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');

        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        return df.format(value);
    }

    public static String toCurrency(BigInteger value) {
        if (value == null)
            return "";

        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.of("id", "ID"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');

        DecimalFormat df = new DecimalFormat("#,##0", symbols);
        return df.format(value);
    }
}
