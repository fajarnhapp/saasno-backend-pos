package com.saasno.utility.generator;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class ID {

    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String number() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("ssSSSHHmmyyyyMMdd"));
    }

    public static String serial() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy-HHmmssSSS"));
    }

    public static String alphabet(int length) {
        String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        SecureRandom RANDOM = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(CHARACTERS.length());
            sb.append(CHARACTERS.charAt(index));
        }
        return sb.toString();
    }

}
