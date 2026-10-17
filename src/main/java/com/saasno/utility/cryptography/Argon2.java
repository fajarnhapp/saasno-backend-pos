package com.saasno.utility.cryptography;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

public class Argon2 {

	public static final Argon2PasswordEncoder setDefault = new Argon2PasswordEncoder(16, 32, 1, 65536, 3);

	public static String encrypt(String text) {
		return setDefault.encode(text);
	}

}
