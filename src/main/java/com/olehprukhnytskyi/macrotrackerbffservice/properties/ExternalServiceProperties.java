package com.olehprukhnytskyi.macrotrackerbffservice.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "services")
public class ExternalServiceProperties {
    @NotBlank
    private String userServiceUrl;

    @NotBlank
    private String intakeServiceUrl;

    @NotBlank
    private String weightServiceUrl;
}
