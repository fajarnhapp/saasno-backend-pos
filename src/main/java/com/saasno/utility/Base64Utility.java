package com.saasno.utility;

import java.util.Base64;

public class Base64Utility {

    public static String UrlEncode(byte[] data) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(data);
    }

    public static byte[] UrlDecode(String base64Url) {
        return Base64.getUrlDecoder()
                .decode(base64Url);
    }

    public static void main(String[] args) throws Exception {
        String json = "{\"sub\":\"1234567890\",\"name\":\"John Doe\"}";
        String encoded = UrlEncode(json.getBytes());
        System.out.println("Encoded: " + encoded);
        String decoded = new String(UrlDecode(encoded));
        System.out.println("Decoded: " + decoded);
    }

}
