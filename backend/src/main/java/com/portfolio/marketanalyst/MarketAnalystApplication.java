package com.portfolio.marketanalyst;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MarketAnalystApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketAnalystApplication.class, args);
    }
}
