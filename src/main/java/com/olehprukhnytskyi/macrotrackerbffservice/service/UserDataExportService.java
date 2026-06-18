package com.olehprukhnytskyi.macrotrackerbffservice.service;

import com.olehprukhnytskyi.exception.ExportNoDataException;
import com.olehprukhnytskyi.exception.ExportValidationException;
import com.olehprukhnytskyi.exception.ExternalServiceException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.IntakeDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.NutrimentsDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.WaterLogDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.WeightLogDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.export.ExportFileDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.export.ExportPeriodDto;
import com.olehprukhnytskyi.util.CustomHeaders;
import com.olehprukhnytskyi.util.UnitType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDataExportService {
    private static final long MAX_RANGE_DAYS = 90;
    private static final String RANGE_LIMIT_MESSAGE =
            "The date range cannot exceed 3 months";
    private static final String NO_DATA_MESSAGE =
            "No data available for export for the selected period";
    private static final Set<ExportPreset> SUPPORTED_PRESETS =
            EnumSet.allOf(ExportPreset.class);
    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

    private final WebClient intakeWebClient;
    private final WebClient weightWebClient;

    public Mono<ExportFileDto> export(Long userId, String preset,
                                      LocalDate startDate, LocalDate endDate) {
        ExportPeriodDto period = resolvePeriod(preset, startDate, endDate);
        Mono<List<IntakeDto>> intakes = fetchIntakes(userId, period);
        Mono<List<WeightLogDto>> weights = fetchWeights(userId, period);
        Mono<List<WaterLogDto>> waterLogs = fetchWaterLogs(userId, period);
        return Mono.zip(intakes, weights, waterLogs)
                .map(tuple -> buildExport(period, tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    private ExportPeriodDto resolvePeriod(String preset, LocalDate startDate, LocalDate endDate) {
        boolean hasPreset = preset != null && !preset.isBlank();
        boolean hasCustomRange = startDate != null || endDate != null;
        if (hasPreset == hasCustomRange) {
            throw new ExportValidationException(
                    "Pass either preset or startDate and endDate");
        }
        ExportPeriodDto period = hasPreset
                ? resolvePreset(preset)
                : resolveCustomRange(startDate, endDate);
        validateRange(period);
        return period;
    }

    private ExportPeriodDto resolvePreset(String value) {
        ExportPreset preset = SUPPORTED_PRESETS.stream()
                .filter(candidate -> candidate.apiValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new ExportValidationException("Unknown preset"));
        LocalDate today = LocalDate.now();
        return switch (preset) {
            case CURRENT_WEEK -> new ExportPeriodDto(
                    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today);
            case LAST_7_DAYS -> new ExportPeriodDto(today.minusDays(6), today);
            case LAST_30_DAYS -> new ExportPeriodDto(today.minusDays(29), today);
            case CURRENT_MONTH -> new ExportPeriodDto(today.withDayOfMonth(1), today);
            default -> throw new ExportValidationException("Unknown preset");
        };
    }

    private ExportPeriodDto resolveCustomRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ExportValidationException("startDate and endDate are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new ExportValidationException("startDate cannot be later than endDate");
        }
        return new ExportPeriodDto(startDate, endDate);
    }

    private void validateRange(ExportPeriodDto period) {
        long daysBetween = ChronoUnit.DAYS.between(period.startDate(), period.endDate());
        if (daysBetween > MAX_RANGE_DAYS) {
            throw new ExportValidationException(RANGE_LIMIT_MESSAGE);
        }
    }

    private Mono<List<IntakeDto>> fetchIntakes(Long userId, ExportPeriodDto period) {
        return intakeWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/intake/range")
                        .queryParam("startDate", period.startDate())
                        .queryParam("endDate", period.endDate())
                        .build())
                .header(CustomHeaders.X_USER_ID, userId.toString())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<IntakeDto>>() {})
                .doOnError(e -> log.error("Failed to fetch export intakes userId={}", userId, e))
                .onErrorMap(e -> new ExternalServiceException(
                        CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE,
                        "Intake service unavailable", e));
    }

    private Mono<List<WeightLogDto>> fetchWeights(Long userId, ExportPeriodDto period) {
        return weightWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/weights/range")
                        .queryParam("startDate", period.startDate())
                        .queryParam("endDate", period.endDate())
                        .build())
                .header(CustomHeaders.X_USER_ID, userId.toString())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<WeightLogDto>>() {})
                .doOnError(e -> log.error("Failed to fetch export weights userId={}", userId, e))
                .onErrorMap(e -> new ExternalServiceException(
                        CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE,
                        "Weight service unavailable", e));
    }

    private Mono<List<WaterLogDto>> fetchWaterLogs(Long userId, ExportPeriodDto period) {
        return weightWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/water/range")
                        .queryParam("startDate", period.startDate())
                        .queryParam("endDate", period.endDate())
                        .build())
                .header(CustomHeaders.X_USER_ID, userId.toString())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<WaterLogDto>>() {})
                .doOnError(e -> log.error("Failed to fetch export water logs userId={}",
                        userId, e))
                .onErrorMap(e -> new ExternalServiceException(
                        CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE,
                        "Weight service unavailable", e));
    }

    private ExportFileDto buildExport(ExportPeriodDto period, List<IntakeDto> intakes,
                                      List<WeightLogDto> weights, List<WaterLogDto> waterLogs) {
        if (intakes.isEmpty() && weights.isEmpty() && waterLogs.isEmpty()) {
            throw new ExportNoDataException(NO_DATA_MESSAGE);
        }

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            createSummarySheet(workbook, headerStyle, period, intakes, weights, waterLogs);
            createFoodLogSheet(workbook, headerStyle, intakes);
            workbook.write(outputStream);
            return new ExportFileDto(filename(period), outputStream.toByteArray());
        } catch (IOException exception) {
            throw new ExternalServiceException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to generate Excel export", exception);
        }
    }

    private void createSummarySheet(Workbook workbook, CellStyle headerStyle,
                                    ExportPeriodDto period, List<IntakeDto> intakes,
                                    List<WeightLogDto> weights,
                                    List<WaterLogDto> waterLogs) {
        Sheet sheet = workbook.createSheet("Summary");
        writeHeader(sheet, headerStyle, List.of(
                "Date",
                "Weight",
                "Water",
                "Total Calories",
                "Carbohydrates",
                "Fat",
                "Protein"));

        Map<LocalDate, List<IntakeDto>> intakesByDate = intakes.stream()
                .collect(Collectors.groupingBy(IntakeDto::getDate));
        Map<LocalDate, WeightLogDto> weightsByDate = weights.stream()
                .collect(Collectors.toMap(
                        WeightLogDto::getDate,
                        Function.identity(),
                        (current, ignored) -> current
                ));
        Map<LocalDate, Integer> waterMlByDate = waterLogs.stream()
                .collect(Collectors.groupingBy(
                        WaterLogDto::getDate,
                        Collectors.summingInt(WaterLogDto::getAmountMl)
                ));

        int rowIndex = 1;
        for (LocalDate date : datesBetween(period)) {
            final List<IntakeDto> dailyIntakes = intakesByDate.getOrDefault(date, List.of());
            Row row = sheet.createRow(rowIndex++);
            writeCell(row, 0, date.toString());
            writeNumberOrBlank(row, 1, Optional.ofNullable(weightsByDate.get(date))
                    .map(WeightLogDto::getWeight)
                    .orElse(null));
            writeCell(row, 2, waterMlByDate.getOrDefault(date, 0));
            writeCell(row, 3, sumNutrient(dailyIntakes, NutrimentsDto::getCalories));
            writeCell(row, 4, sumNutrient(dailyIntakes, NutrimentsDto::getCarbohydrates));
            writeCell(row, 5, sumNutrient(dailyIntakes, NutrimentsDto::getFat));
            writeCell(row, 6, sumNutrient(dailyIntakes, NutrimentsDto::getProtein));
        }
        autosize(sheet, 7);
    }

    private void createFoodLogSheet(Workbook workbook, CellStyle headerStyle,
                                    List<IntakeDto> intakes) {
        Sheet sheet = workbook.createSheet("Food Log");
        writeHeader(sheet, headerStyle, List.of(
                "Date",
                "Meal",
                "Food",
                "Amount",
                "Unit",
                "Equivalent in Grams",
                "Calories",
                "Protein",
                "Fat",
                "Carbohydrates"));

        List<IntakeDto> sortedIntakes = intakes.stream()
                .sorted(Comparator.comparing(IntakeDto::getDate)
                        .thenComparing(IntakeDto::getIntakePeriod)
                        .thenComparing(IntakeDto::getId))
                .toList();
        int rowIndex = 1;
        for (IntakeDto intake : sortedIntakes) {
            Row row = sheet.createRow(rowIndex++);
            final NutrimentsDto nutrients = intake.getNutriments() == null
                    ? new NutrimentsDto()
                    : intake.getNutriments();
            writeCell(row, 0, intake.getDate().toString());
            writeCell(row, 1, String.valueOf(intake.getIntakePeriod()));
            writeCell(row, 2, intake.getFoodName());
            writeCell(row, 3, intake.getAmount());
            writeCell(row, 4, String.valueOf(intake.getUnitType()));
            writeNumberOrBlank(row, 5, gramEquivalent(intake));
            writeCell(row, 6, nullToZero(nutrients.getCalories()));
            writeCell(row, 7, nullToZero(nutrients.getCarbohydrates()));
            writeCell(row, 8, nullToZero(nutrients.getFat()));
            writeCell(row, 9, nullToZero(nutrients.getProtein()));
        }
        autosize(sheet, 10);
    }

    private BigDecimal gramEquivalent(IntakeDto intake) {
        // Pieces do not carry grams-per-piece today, so exporting null keeps the value honest.
        return intake.getUnitType() == UnitType.GRAMS
                ? BigDecimal.valueOf(intake.getAmount())
                : null;
    }

    private List<LocalDate> datesBetween(ExportPeriodDto period) {
        // Inclusive day count is used because both query params represent user-visible dates.
        long inclusiveDayCount = ChronoUnit.DAYS.between(period.startDate(), period.endDate()) + 1;
        return period.startDate().datesUntil(period.endDate().plusDays(1), Period.ofDays(1))
                .limit(inclusiveDayCount)
                .toList();
    }

    private BigDecimal sumNutrient(List<IntakeDto> intakes,
                                   Function<NutrimentsDto, BigDecimal> extractor) {
        return intakes.stream()
                .map(IntakeDto::getNutriments)
                .map(nutrients -> nutrients == null ? BigDecimal.ZERO : extractor.apply(nutrients))
                .map(this::nullToZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle, List<String> values) {
        Row row = sheet.createRow(0);
        for (int index = 0; index < values.size(); index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(values.get(index));
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeCell(Row row, int index, String value) {
        row.createCell(index).setCellValue(value == null ? "" : value);
    }

    private void writeCell(Row row, int index, int value) {
        row.createCell(index).setCellValue(value);
    }

    private void writeCell(Row row, int index, BigDecimal value) {
        row.createCell(index).setCellValue(nullToZero(value).doubleValue());
    }

    private void writeNumberOrBlank(Row row, int index, BigDecimal value) {
        if (value == null) {
            row.createCell(index).setBlank();
            return;
        }
        writeCell(row, index, value);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void autosize(Sheet sheet, int columns) {
        for (int index = 0; index < columns; index++) {
            sheet.autoSizeColumn(index);
        }
    }

    private String filename(ExportPeriodDto period) {
        return "macro-tracker-export-%s-%s.xlsx".formatted(
                FILE_DATE_FORMAT.format(period.startDate()),
                FILE_DATE_FORMAT.format(period.endDate()));
    }

    private enum ExportPreset {
        LAST_7_DAYS("last_7_days"),
        LAST_30_DAYS("last_30_days"),
        CURRENT_WEEK("current_week"),
        CURRENT_MONTH("current_month");

        private final String apiValue;

        ExportPreset(String apiValue) {
            this.apiValue = apiValue;
        }
    }
}
