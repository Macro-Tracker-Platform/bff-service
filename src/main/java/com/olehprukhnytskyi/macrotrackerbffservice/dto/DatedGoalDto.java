package com.olehprukhnytskyi.macrotrackerbffservice.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class DatedGoalDto {
    private LocalDate date;
    private UserGoalDto goal;
}
