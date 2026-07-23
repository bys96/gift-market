package com.giftmarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class GiftmarketApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(GiftmarketApiApplication.class, args);
	}

}
