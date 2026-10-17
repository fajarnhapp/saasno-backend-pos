package com.saasno.utility.logger;

import java.util.regex.Pattern;

public class LogMasker {

    private static final String MASK = "******";
    private static final String SENSITIVE_KEYS = "password|oldPassword|newPassword|passwordConfirmation|otp|secret|pin|setupUri";
    private static final Pattern SENSITIVE_PARAM = Pattern
            .compile("(?i)((?:^|&)(?:" + SENSITIVE_KEYS + ")=)[^&]*");
    private static final Pattern SENSITIVE_JSON_FIELD = Pattern
            .compile("(?i)(\"(?:" + SENSITIVE_KEYS + ")\"\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\"");

    private LogMasker() {
    }

    public static String queryString(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return null;
        }
        return SENSITIVE_PARAM.matcher(queryString).replaceAll("$1" + MASK);
    }

    public static String body(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        return SENSITIVE_JSON_FIELD.matcher(body).replaceAll("$1\"" + MASK + "\"");
    }
}
