package com.dogfood.backend.webhook;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

@Service
public class WebhookService {

    private static final int MAX_ATTEMPTS = 3;

    private final WebhookStore store;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public WebhookService(
            WebhookStore store,
            JsonMapper jsonMapper
    ) {
        this.store = store;
        this.jsonMapper = jsonMapper;
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(3)
                        )
                        .build();
    }

    @EventListener
    public void handleAuditEvent(
            AuditEventCreated created
    ) {
        JsonNode event = created.event();

        String eventType =
                event.path("action").asText("UNKNOWN");

        for (WebhookStore.Registration webhook :
                store.activeRegistrations()) {

            if (!matches(
                    webhook.events(),
                    eventType
            )) {
                continue;
            }

            deliver(webhook, event);
        }
    }

    private boolean matches(
            java.util.List<String> subscriptions,
            String eventType
    ) {
        return subscriptions.isEmpty()
                || subscriptions.contains("*")
                || subscriptions.contains(eventType);
    }

    private void deliver(
            WebhookStore.Registration webhook,
            JsonNode auditEvent
    ) {
        String eventId =
                auditEvent.path("id").asText();

        String eventType =
                auditEvent.path("action")
                        .asText("UNKNOWN");

        ObjectNode payload =
                jsonMapper.createObjectNode();

        payload.put("id", eventId);
        payload.put("type", eventType);
        payload.put(
                "timestamp",
                auditEvent.path("timestamp").asText()
        );
        payload.put(
                "request_id",
                auditEvent.path("request_id").asText()
        );
        payload.put(
                "actor",
                auditEvent.path("actor").asText()
        );
        payload.put(
                "target",
                auditEvent.path("target").asText()
        );
        payload.put(
                "reason",
                auditEvent.path("reason").asText()
        );

        String body;

        try {
            body =
                    jsonMapper.writeValueAsString(payload);
        } catch (Exception e) {
            store.recordDelivery(
                    webhook.id(),
                    eventId,
                    eventType,
                    0,
                    null,
                    false,
                    "Could not serialize webhook payload"
            );
            return;
        }

        int attempts = 0;
        Integer statusCode = null;
        String error = null;

        while (attempts < MAX_ATTEMPTS) {
            attempts++;

            try {
                String timestamp =
                        Long.toString(
                                Instant.now().getEpochSecond()
                        );

                String signature =
                        sign(
                                webhook.secret(),
                                timestamp + "." + body
                        );

                HttpRequest request =
                        HttpRequest.newBuilder(
                                        URI.create(
                                                webhook.url()
                                        )
                                )
                                .timeout(
                                        Duration.ofSeconds(5)
                                )
                                .header(
                                        "Content-Type",
                                        "application/json"
                                )
                                .header(
                                        "User-Agent",
                                        "DogFood-Webhooks/1.0"
                                )
                                .header(
                                        "X-DogFood-Event-Id",
                                        eventId
                                )
                                .header(
                                        "X-DogFood-Event",
                                        eventType
                                )
                                .header(
                                        "X-DogFood-Timestamp",
                                        timestamp
                                )
                                .header(
                                        "X-DogFood-Signature",
                                        "sha256=" + signature
                                )
                                .POST(
                                        HttpRequest.BodyPublishers
                                                .ofString(body)
                                )
                                .build();

                HttpResponse<String> response =
                        httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                        );

                statusCode = response.statusCode();

                if (statusCode >= 200
                        && statusCode < 300) {

                    store.recordDelivery(
                            webhook.id(),
                            eventId,
                            eventType,
                            attempts,
                            statusCode,
                            true,
                            null
                    );

                    return;
                }

                error =
                        "HTTP " + statusCode;

            } catch (Exception e) {
                error = e.getClass().getSimpleName()
                        + ": "
                        + e.getMessage();
            }

            if (attempts < MAX_ATTEMPTS) {
                sleepBeforeRetry(attempts);
            }
        }

        store.recordDelivery(
                webhook.id(),
                eventId,
                eventType,
                attempts,
                statusCode,
                false,
                error
        );
    }

    private static void sleepBeforeRetry(
            int attempt
    ) {
        try {
            Thread.sleep(
                    switch (attempt) {
                        case 1 -> 150L;
                        case 2 -> 400L;
                        default -> 800L;
                    }
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static String sign(
            String secret,
            String value
    ) {
        try {
            Mac mac =
                    Mac.getInstance("HmacSHA256");

            mac.init(
                    new SecretKeySpec(
                            secret.getBytes(
                                    StandardCharsets.UTF_8
                            ),
                            "HmacSHA256"
                    )
            );

            byte[] digest =
                    mac.doFinal(
                            value.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder result =
                    new StringBuilder();

            for (byte b : digest) {
                result.append(
                        String.format("%02x", b)
                );
            }

            return result.toString();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not sign webhook",
                    e
            );
        }
    }
}
