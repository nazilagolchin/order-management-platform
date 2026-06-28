package com.nazila.ordermgmt.shared.autoconfigure;

import com.nazila.ordermgmt.shared.correlation.CorrelationIdFilter;
import com.nazila.ordermgmt.shared.error.GlobalApiExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Auto-registers the shared-kernel building blocks in every service that
 * depends on this module, without requiring services to widen their
 * {@code @ComponentScan} to a common base package.
 */
@AutoConfiguration
@ConditionalOnWebApplication
public class SharedKernelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalApiExceptionHandler globalApiExceptionHandler() {
        return new GlobalApiExceptionHandler();
    }
}
