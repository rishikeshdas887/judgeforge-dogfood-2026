package com.dogfood.backend.controller;

import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.security.RequestIdFilter;
import com.dogfood.backend.service.AuditStore;
import com.dogfood.backend.service.EventStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

@RestController
public class EventController {

    private final AuthService authService;
    private final EventStore eventStore;
    private final AuditStore auditStore;

    public EventController(
            AuthService authService,
            EventStore eventStore,
            AuditStore auditStore
    ) {
        this.authService = authService;
        this.eventStore = eventStore;
        this.auditStore = auditStore;
    }

    @GetMapping("/api/organizer/event")
    public ResponseEntity<?> getEvent(
            HttpServletRequest request
    ) {
        if (!isOrganizer(request)) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        return ResponseEntity.ok(eventStore.read());
    }

    @PostMapping("/api/organizer/events")
    public ResponseEntity<?> createEvent(
            @RequestBody JsonNode body,
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        if (user.get().role() != AuthService.Role.ORGANIZER) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        try {
            if (body == null || !body.isObject()) {
                return ResponseEntity.badRequest()
                        .body("Event must be a JSON object");
            }

            ObjectNode event =
                    (ObjectNode) body.deepCopy();

            if (event.path("id").asText("").isBlank()) {
                event.put(
                        "id",
                        "evt_" + UUID.randomUUID()
                );
            }

            ObjectNode created =
                    eventStore.write(event);

            auditStore.append(
                    user.get().id(),
                    "EVENT_CREATED",
                    "event:" + created.path("id").asText(),
                    null,
                    created,
                    "event_created",
                    RequestIdFilter.getRequestId(request)
            );

            return ResponseEntity.status(201)
                    .body(created);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

    @PutMapping("/api/organizer/event")
    public ResponseEntity<?> updateEvent(
            @RequestBody JsonNode body,
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        if (user.get().role() != AuthService.Role.ORGANIZER) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        try {
            if (body == null || !body.isObject()) {
                return ResponseEntity.badRequest()
                        .body("Event must be a JSON object");
            }

            ObjectNode before =
                    eventStore.read();

            ObjectNode event =
                    (ObjectNode) body.deepCopy();

            String currentId =
                    before.path("id").asText("");

            if (event.path("id").asText("").isBlank()) {
                event.put("id", currentId);
            }

            if (!currentId.isBlank()
                    && !currentId.equals(
                            event.path("id").asText()
                    )) {
                return ResponseEntity.badRequest()
                        .body("Event id cannot change during update");
            }

            ObjectNode updated =
                    eventStore.write(event);

            auditStore.append(
                    user.get().id(),
                    "EVENT_CONFIG_UPDATED",
                    "event:" + updated.path("id").asText(),
                    before,
                    updated,
                    "event_config_updated",
                    RequestIdFilter.getRequestId(request)
            );

            return ResponseEntity.ok(updated);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

    private boolean isOrganizer(
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        return user.isPresent()
                && user.get().role()
                == AuthService.Role.ORGANIZER;
    }
}
