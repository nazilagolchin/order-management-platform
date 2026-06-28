package com.nazila.ordermgmt.inventory.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI inventoryServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Inventory Service API")
                .version("v1")
                .description("""
                        Owns the inventory aggregate for the Cloud-Native Order Management Platform.
                        Reserves stock by consuming OrderCreatedEvent from Kafka; the read endpoint
                        here exists to inspect stock levels, not to drive the saga.
                        """)
                .contact(new Contact().name("Nazila Golchin")));
    }
}
