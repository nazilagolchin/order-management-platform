package com.nazila.ordermgmt.shared.autoconfigure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Every event consumer reads {@code EventEnvelope.payload()} generically
 * (typed {@code Object}, since the envelope is shared across event types)
 * and then converts it to a concrete event with {@code ObjectMapper#convertValue}.
 * Without this customization, Jackson's untyped deserialization turns a
 * decimal JSON number into a {@code Double} first, and converting a
 * {@code Double} on to {@code BigDecimal} loses the original scale (e.g.
 * {@code 5.00} becomes {@code 5.0}) — silently inconsistent with the
 * {@code NUMERIC(19,2)} money semantics every service's schema relies on.
 * Enabling {@code USE_BIG_DECIMAL_FOR_FLOATS} makes that intermediate step
 * use {@code BigDecimal} directly, preserving scale end to end.
 */
@AutoConfiguration
public class JacksonMoneyAutoConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer bigDecimalForFloatsCustomizer() {
        return builder -> builder.featuresToEnable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }
}
