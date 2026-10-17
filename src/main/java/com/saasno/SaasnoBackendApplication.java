package com.saasno;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.saasno.utility.logger.Log;

@EnableScheduling
@SpringBootApplication
public class SaasnoBackendApplication implements CommandLineRunner {

	public static void main(String[] args) {
		try {
			SpringApplication.run(SaasnoBackendApplication.class, args);
		} catch (Exception e) {
			Log.error("Error running saasno backend : ", e);
		}
	}

	@Override
	public void run(String... args) throws Exception {
	}

}
