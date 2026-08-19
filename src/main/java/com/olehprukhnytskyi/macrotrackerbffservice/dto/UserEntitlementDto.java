package com.olehprukhnytskyi.macrotrackerbffservice.dto;

import lombok.Data;

@Data
public class UserEntitlementDto {
    private String plan;
    private Features features;

    @Data
    public static class Features {
        private boolean advancedInsights;
    }
}
