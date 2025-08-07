package com.recargapay.walletservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Wallet Service Assignment")
                        .description("A microservice for managing wallet operations including deposits, withdrawals, transfers, and historical balance queries.")
                        .version("v1"));
    }
}
