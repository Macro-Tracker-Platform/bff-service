package com.olehprukhnytskyi.macrotrackerbffservice.dto.export;

import java.time.LocalDate;

public record ExportPeriodDto(LocalDate startDate, LocalDate endDate) {
}
