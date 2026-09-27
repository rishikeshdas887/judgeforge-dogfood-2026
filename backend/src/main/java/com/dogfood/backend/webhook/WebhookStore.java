package com.dogfood.backend.webhook;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WebhookStore {

    public record Registration(
            String id,
            String url,
            List<String> events,
            String secret,
            String createdAt,
            boolean active
    ) {
    }

    private final JsonMapper jsonMapper;
    private final Path path;

    @Autowired
    public WebhookStore(JsonMapper jsonMapper) {
        this(jsonMapper, Path.of(
                System.getProperty(
                        "dogfood.webhooks.path",
                        "data/webhooks.json"
                )
        ));
    }

    public WebhookStore(
            JsonMapper jsonMapper,
            Path path
    ) {
        this.jsonMapper = jsonMapper;
        this.path = path;

        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                writeRoot(newRoot());
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not initialize webhook storage",
                    e
            );
        }
    }

    public synchronized Registration create(
            String url,
            List<String> events
    ) {
        ObjectNode document = root();
        ArrayNode webhooks = document.withArray("webhooks");

        String id = "wh_" + UUID.randomUUID();

        byte[] secretBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(secretBytes);

        String secret =
                "whsec_" +
                        Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(secretBytes);

        String createdAt = Instant.now().toString();

        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("id", id);
        node.put("url", url);
        node.putArray("events").addAll(
                toTextArray(events)
        );
        node.put("secret", secret);
        node.put("created_at", createdAt);
        node.put("active", true);

        webhooks.add(node);
        writeRoot(document);

        return new Registration(
                id,
                url,
                List.copyOf(events),
                secret,
                createdAt,
                true
        );
    }

    public synchronized List<Registration> activeRegistrations() {
        List<Registration> result = new ArrayList<>();

        for (JsonNode node : root().withArray("webhooks")) {
            if (!node.path("active").asBoolean(false)) {
                continue;
            }

            List<String> events = new ArrayList<>();

            for (JsonNode event : node.withArray("events")) {
                events.add(event.asText());
            }

            result.add(
                    new Registration(
                            node.path("id").asText(),
                            node.path("url").asText(),
                            List.copyOf(events),
                            node.path("secret").asText(),
                            node.path("created_at").asText(),
                            true
                    )
            );
        }

        return result;
    }

    public synchronized List<JsonNode> publicRegistrations() {
        List<JsonNode> result = new ArrayList<>();

        for (JsonNode node : root().withArray("webhooks")) {
            ObjectNode safe =
                    JsonNodeFactory.instance.objectNode();

            safe.put("id", node.path("id").asText());
            safe.put("url", node.path("url").asText());
            safe.set(
                    "events",
                    node.withArray("events").deepCopy()
            );
            safe.put(
                    "created_at",
                    node.path("created_at").asText()
            );
            safe.put(
                    "active",
                    node.path("active").asBoolean(false)
            );

            result.add(safe);
        }

        return result;
    }

    public synchronized boolean deactivate(String id) {
        ObjectNode document = root();
        ArrayNode webhooks = document.withArray("webhooks");

        for (JsonNode node : webhooks) {
            if (id.equals(node.path("id").asText())) {
                ((ObjectNode) node).put("active", false);
                writeRoot(document);
                return true;
            }
        }

        return false;
    }

    public synchronized Optional<Registration> find(
            String id
    ) {
        for (Registration registration :
                activeRegistrations()) {
            if (registration.id().equals(id)) {
                return Optional.of(registration);
            }
        }

        return Optional.empty();
    }

    public synchronized void recordDelivery(
            String webhookId,
            String eventId,
            String eventType,
            int attempts,
            Integer statusCode,
            boolean delivered,
            String error
    ) {
        ObjectNode document = root();
        ArrayNode deliveries =
                document.withArray("deliveries");

        ObjectNode delivery =
                JsonNodeFactory.instance.objectNode();

        delivery.put(
                "id",
                "wd_" + UUID.randomUUID()
        );
        delivery.put("webhook_id", webhookId);
        delivery.put("event_id", eventId);
        delivery.put("event_type", eventType);
        delivery.put("attempts", attempts);

        if (statusCode == null) {
            delivery.putNull("status_code");
        } else {
            delivery.put("status_code", statusCode);
        }

        delivery.put("delivered", delivered);
        delivery.put(
                "timestamp",
                Instant.now().toString()
        );

        if (error == null || error.isBlank()) {
            delivery.putNull("error");
        } else {
            delivery.put("error", error);
        }

        deliveries.add(delivery);
        writeRoot(document);
    }

    public synchronized List<JsonNode> deliveries() {
        return new ArrayList<>(
                root().withArray("deliveries").findValues("id")
        );
    }

    private ObjectNode root() {
        try {
            JsonNode node =
                    jsonMapper.readTree(
                            Files.readString(path)
                    );

            if (!node.isObject()) {
                throw new IllegalStateException(
                        "webhooks.json must contain an object"
                );
            }

            ObjectNode object = (ObjectNode) node;

            if (!object.path("webhooks").isArray()) {
                object.putArray("webhooks");
            }

            if (!object.path("deliveries").isArray()) {
                object.putArray("deliveries");
            }

            return object;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read webhook storage",
                    e
            );
        }
    }

    private void writeRoot(ObjectNode document) {
        try {
            String content =
                    jsonMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(document);

            Path temp =
                    path.resolveSibling(
                            "webhooks.json.tmp"
                    );

            Files.writeString(temp, content);

            try {
                Files.move(
                        temp,
                        path,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (
                    java.nio.file.AtomicMoveNotSupportedException e
            ) {
                Files.move(
                        temp,
                        path,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not write webhook storage",
                    e
            );
        }
    }

    private static ObjectNode newRoot() {
        ObjectNode root =
                JsonNodeFactory.instance.objectNode();

        root.putArray("webhooks");
        root.putArray("deliveries");

        return root;
    }

    private static ArrayNode toTextArray(
            List<String> values
    ) {
        ArrayNode result =
                JsonNodeFactory.instance.arrayNode();

        for (String value : values) {
            result.add(value);
        }

        return result;
    }
}
