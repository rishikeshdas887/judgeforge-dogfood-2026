package com.dogfood.backend.controller;

import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.webhook.WebhookStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/organizer/webhooks")
public class WebhookController {

    private final AuthService authService;
    private final WebhookStore store;

    public WebhookController(
            AuthService authService,
            WebhookStore store
    ) {
        this.authService = authService;
        this.store = store;
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody JsonNode body,
            HttpServletRequest request
    ) {
        if (!isOrganizer(request)) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        if (body == null || !body.isObject()) {
            return ResponseEntity.badRequest()
                    .body("Webhook body must be a JSON object");
        }

        String url =
                body.path("url")
                        .asText("")
                        .trim();

        if (!isHttpUrl(url)) {
            return ResponseEntity.badRequest()
                    .body("url must be an http or https URL");
        }

        List<String> events =
                new ArrayList<>();

        JsonNode eventNode =
                body.path("events");

        if (eventNode.isArray()) {
            ArrayNode array =
                    (ArrayNode) eventNode;

            for (JsonNode node : array) {
                String value =
                        node.asText("")
                                .trim();

                if (!value.isBlank()) {
                    events.add(value);
                }
            }
        }

        if (events.isEmpty()) {
            events.add("*");
        }

        if (events.size() > 50) {
            return ResponseEntity.badRequest()
                    .body("At most 50 event types are allowed");
        }

        WebhookStore.Registration created =
                store.create(
                        url,
                        List.copyOf(events)
                );

        var response =
                new java.util.LinkedHashMap<String, Object>();

        response.put("id", created.id());
        response.put("url", created.url());
        response.put("events", created.events());
        response.put("created_at", created.createdAt());

        // Returned only at creation time.
        response.put("secret", created.secret());

        return ResponseEntity.status(201)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<?> list(
            HttpServletRequest request
    ) {
        if (!isOrganizer(request)) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        return ResponseEntity.ok(
                store.publicRegistrations()
        );
    }

    @DeleteMapping("/{webhookId}")
    public ResponseEntity<?> delete(
            @PathVariable String webhookId,
            HttpServletRequest request
    ) {
        if (!isOrganizer(request)) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        if (!store.deactivate(webhookId)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }

    private boolean isOrganizer(
            HttpServletRequest request
    ) {
        var user =
                authService.currentUser(request);

        return user.isPresent()
                && AuthService.isOrganizerOrAdmin(
                        user.get().role()
                );
    }

    private boolean isHttpUrl(String raw) {
        try {
            URI uri = URI.create(raw);

            return "http".equalsIgnoreCase(
                    uri.getScheme()
            ) || "https".equalsIgnoreCase(
                    uri.getScheme()
            );

        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
