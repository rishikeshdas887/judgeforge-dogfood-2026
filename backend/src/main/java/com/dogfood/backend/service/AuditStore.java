package com.dogfood.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuditStore {

    private final JsonMapper jsonMapper;
    private final Path auditPath;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    public AuditStore(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.auditPath = Path.of("data/audit-events.json");

        try {
            Files.createDirectories(auditPath.getParent());

            if (!Files.exists(auditPath)) {
                write(jsonMapper.createArrayNode());
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not initialize audit storage", e
            );
        }
    }

    public synchronized void append(
            String actor,
            String action,
            String target,
            JsonNode before,
            JsonNode after,
            String reason,
            String requestId
    ) {
        ArrayNode events = readAll();

        ObjectNode event = jsonMapper.createObjectNode();

        event.put("id", "aud_" + UUID.randomUUID());
        event.put(
                "actor",
                actor == null ? "system" : actor
        );
        event.put("action", action);
        event.put("target", target);
        event.put("timestamp", Instant.now().toString());

        if (before == null) {
            event.putNull("before");
        } else {
            event.set("before", before.deepCopy());
        }

        if (after == null) {
            event.putNull("after");
        } else {
            event.set("after", after.deepCopy());
        }

        event.put(
                "reason",
                reason == null || reason.isBlank()
                        ? "unspecified"
                        : reason
        );

        event.put(
                "request_id",
                requestId == null || requestId.isBlank()
                        ? "unknown"
                        : requestId
        );

        events.add(event);

        write(events);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(
                    new com.dogfood.backend.webhook.AuditEventCreated(
                            event.deepCopy()
                    )
            );
        }
    }

    public synchronized ArrayNode readAll() {
        try {
            JsonNode root =
                    jsonMapper.readTree(
                            Files.readString(auditPath)
                    );

            if (!root.isArray()) {
                throw new IllegalStateException(
                        "audit-events.json must contain an array"
                );
            }

            return (ArrayNode) root;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read audit events",
                    e
            );
        }
    }

    private void write(ArrayNode events) {
        try {
            String content =
                    jsonMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(events);

            Path tempPath =
                    auditPath.resolveSibling(
                            "audit-events.json.tmp"
                    );

            Files.writeString(
                    tempPath,
                    content
            );

            try {
                Files.move(
                        tempPath,
                        auditPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                );
            } catch (
                    java.nio.file.AtomicMoveNotSupportedException e
            ) {
                Files.move(
                        tempPath,
                        auditPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not write audit events",
                    e
            );
        }
    }
}
