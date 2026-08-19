package com.olehprukhnytskyi.macrotrackerbffservice.service;

import com.olehprukhnytskyi.dto.PagedResponse;
import com.olehprukhnytskyi.exception.ExternalServiceException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.DashboardDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.IntakeDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.UserDetailsDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.UserGoalDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.WeightLogDto;
import com.olehprukhnytskyi.util.CustomHeaders;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {
    private static final String APP_VERSION_CODE_HEADER = "X-App-Version-Code";
    private final WebClient userWebClient;
    private final WebClient intakeWebClient;
    private final WebClient weightWebClient;

    public Mono<DashboardDto> getDashboard(Long userId, LocalDate date) {
        return getDashboard(userId, date, null);
    }

    public Mono<DashboardDto> getDashboard(Long userId, LocalDate date, String appVersionCode) {
        log.debug("Fetching dashboard data for userId={}", userId);
        Mono<UserGoalDto> userGoalMono = userWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/profile/goal")
                        .queryParam("date", date).build())
                .headers(headers -> {
                    headers.set(CustomHeaders.X_USER_ID, userId.toString());
                    if (appVersionCode != null && !appVersionCode.isBlank()) {
                        headers.set(APP_VERSION_CODE_HEADER, appVersionCode);
                    }
                })
                .retrieve()
                .bodyToMono(UserGoalDto.class)
                .doOnError(e -> log.error("Failed to fetch user goals for userId={}", userId, e))
                .onErrorMap(e -> new ExternalServiceException(
                        CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE,
                        "User service unavailable", e));
        Mono<List<IntakeDto>> intakesMono = intakeWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/intake")
                        .queryParam("date", date)
                        .build())
                .headers(headers -> {
                    headers.set(CustomHeaders.X_USER_ID, userId.toString());
                    if (appVersionCode != null && !appVersionCode.isBlank()) {
                        headers.set(APP_VERSION_CODE_HEADER, appVersionCode);
                    }
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<IntakeDto>>() {})
                .doOnError(e -> log.error("Failed to fetch intakes for userId={}", userId, e))
                .onErrorMap(e -> new ExternalServiceException(
                        CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE,
                        "Intake service unavailable", e));
        Mono<UserDetailsDto> userDetailsMono = userWebClient.get()
                .uri("/api/profile/details")
                .header(CustomHeaders.X_USER_ID, userId.toString())
                .retrieve()
                .bodyToMono(UserDetailsDto.class)
                .doOnError(e -> log.error("Failed to fetch user details for userId={}", userId, e))
                .onErrorMap(e -> new ExternalServiceException(
                        CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE,
                        "User service unavailable", e));
        Mono<PagedResponse<WeightLogDto>> weightLogsMono = weightWebClient.get()
                .uri("/api/weights")
                .header(CustomHeaders.X_USER_ID, userId.toString())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<PagedResponse<WeightLogDto>>() {})
                .doOnError(e -> log.error("Failed to fetch user weight logs for userId={}",
                        userId, e))
                .onErrorMap(e -> new ExternalServiceException(
                        CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE,
                        "Weight service unavailable", e));
        return Mono.zip(userGoalMono, intakesMono, userDetailsMono, weightLogsMono)
                .map(tuple -> {
                    DashboardDto dto = DashboardDto.builder()
                            .goal(tuple.getT1())
                            .intakes(tuple.getT2())
                            .profile(tuple.getT3())
                            .weightLogs(tuple.getT4())
                            .build();
                    log.debug("Successfully built dashboard DTO for userId={}", userId);
                    return dto;
                })
                .doOnError(e -> log
                        .error("Error combining dashboard data for userId={}", userId, e));
    }
}
