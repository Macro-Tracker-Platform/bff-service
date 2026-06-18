package com.olehprukhnytskyi.macrotrackerbffservice.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class WaterLogDto {
    private long id;
    private int amountMl;
    private long createdAt;
    private LocalDate date;
}
