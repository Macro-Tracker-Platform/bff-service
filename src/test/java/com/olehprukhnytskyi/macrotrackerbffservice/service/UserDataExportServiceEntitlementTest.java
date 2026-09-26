package com.olehprukhnytskyi.macrotrackerbffservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.olehprukhnytskyi.exception.ExportNoDataException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UserDataExportServiceEntitlementTest {
    @Test
    void exportRejectsFreeUserBeforeFetchingTrackerData() {
        WebClient userClient = WebClient.builder()
                .exchangeFunction(request -> json(
                        "{\"features\":{\"trainerExport\":false}}"))
                .build();
        AtomicBoolean trackerDataRequested = new AtomicBoolean();
        WebClient trackerClient = WebClient.builder()
                .exchangeFunction(request -> {
                    trackerDataRequested.set(true);
                    return Mono.error(new AssertionError(
                            "Tracker data must not be fetched for a free user"));
                })
                .build();
        UserDataExportService service = new UserDataExportService(
                userClient, trackerClient, trackerClient);

        StepVerifier.create(service.export(42L, "last_7_days", null, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN);
                })
                .verify();

        assertThat(trackerDataRequested).isFalse();
    }

    @Test
    void exportFetchesTrackerDataWhenServerGrantsTrainerExport() {
        WebClient userClient = WebClient.builder()
                .exchangeFunction(request -> json(
                        "{\"features\":{\"trainerExport\":true}}"))
                .build();
        AtomicInteger trackerDataRequests = new AtomicInteger();
        WebClient trackerClient = WebClient.builder()
                .exchangeFunction(request -> {
                    trackerDataRequests.incrementAndGet();
                    return json("[]");
                })
                .build();
        UserDataExportService service = new UserDataExportService(
                userClient, trackerClient, trackerClient);

        StepVerifier.create(service.export(42L, "last_7_days", null, null))
                .expectError(ExportNoDataException.class)
                .verify();

        assertThat(trackerDataRequests).hasValue(3);
    }

    private static Mono<ClientResponse> json(String body) {
        return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(body)
                .build());
    }
}
