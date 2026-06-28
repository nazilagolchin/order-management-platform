package com.nazila.ordermgmt.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Order Service API")
                .version("v1")
                .description("""
                        Owns the order aggregate for the Cloud-Native Order Management Platform.
                        Part of an event-driven saga spanning order, inventory, payment and
                        notification services — see the platform README for the full architecture.
                        """)
                .contact(new Contact().name("Nazila Golchin")));
    }
}
