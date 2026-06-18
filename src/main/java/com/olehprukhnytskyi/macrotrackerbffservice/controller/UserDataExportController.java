package com.olehprukhnytskyi.macrotrackerbffservice.controller;

import com.olehprukhnytskyi.macrotrackerbffservice.dto.export.ExportFileDto;
import com.olehprukhnytskyi.macrotrackerbffservice.service.UserDataExportService;
import com.olehprukhnytskyi.util.CustomHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/export")
@Tag(
        name = "User Data Export API",
        description = "Export user tracker data as Excel"
)
public class UserDataExportController {
    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final UserDataExportService exportService;

    @Operation(
            summary = "Export user tracker data",
            description = "Generates .xlsx file with daily summary and food log sheets"
    )
    @GetMapping("/user-data")
    public Mono<ResponseEntity<Object>> exportUserData(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestParam(required = false) String preset,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Export requested userId={} preset={} startDate={} endDate={}",
                userId, preset, startDate, endDate);
        return exportService.export(userId, preset, startDate, endDate)
                .map(this::fileResponse);
    }

    private ResponseEntity<Object> fileResponse(ExportFileDto file) {
        ByteArrayResource resource = new ByteArrayResource(file.content());
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .contentLength(file.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename())
                        .build()
                        .toString())
                .body(resource);
    }
}
