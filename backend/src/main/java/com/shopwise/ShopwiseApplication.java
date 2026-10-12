package com.shopwise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the Shopwise commerce API and its AI shopping assistant.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ShopwiseApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopwiseApplication.class, args);
    }
}
