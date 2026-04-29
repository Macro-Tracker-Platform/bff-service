package com.olehprukhnytskyi.macrotrackerbffservice.config;

import com.olehprukhnytskyi.macrotrackerbffservice.properties.ExternalServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {
    private final ExternalServiceProperties externalServiceProperties;

    @Bean
    public WebClient userWebClient(WebClient.Builder builder) {
        return builder.baseUrl(externalServiceProperties.getUserServiceUrl()).build();
    }

    @Bean
    public WebClient intakeWebClient(WebClient.Builder builder) {
        return builder.baseUrl(externalServiceProperties.getIntakeServiceUrl()).build();
    }

    @Bean
    public WebClient weightWebClient(WebClient.Builder builder) {
        return builder.baseUrl(externalServiceProperties.getWeightServiceUrl()).build();
    }
}
