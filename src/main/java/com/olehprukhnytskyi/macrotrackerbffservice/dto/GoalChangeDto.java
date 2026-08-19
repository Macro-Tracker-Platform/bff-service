package com.olehprukhnytskyi.macrotrackerbffservice.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class GoalChangeDto {
    private LocalDate lastChangedAt;
}
