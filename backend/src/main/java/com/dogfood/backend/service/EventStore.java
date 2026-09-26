package com.dogfood.backend.service;

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

@Service
public class EventStore {

    private final JsonMapper jsonMapper;
    private final FixtureStore fixtureStore;
    private final Path eventPath;

    public EventStore(
            JsonMapper jsonMapper,
            FixtureStore fixtureStore
    ) {
        this.jsonMapper = jsonMapper;
        this.fixtureStore = fixtureStore;
        this.eventPath = Path.of("/app/data/event.json");

        try {
            Files.createDirectories(eventPath.getParent());

            if (!Files.exists(eventPath)) {
                seedFromFixtures();
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not initialize event storage",
                    e
            );
        }
    }

    public synchronized ObjectNode read() {
        try {
            JsonNode root = jsonMapper.readTree(
                    Files.readString(eventPath)
            );

            if (!root.isObject()
                    || !root.path("event").isObject()) {
                throw new IllegalStateException(
                        "event.json must contain an event object"
                );
            }

            return (ObjectNode) root.path("event");
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read event storage",
                    e
            );
        }
    }

    public synchronized ObjectNode write(
            JsonNode event
    ) {
        validateEvent(event);

        ObjectNode root =
                JsonNodeFactory.instance.objectNode();

        root.put("version", 1);
        root.set("event", event.deepCopy());

        writeRoot(root);

        return (ObjectNode) event.deepCopy();
    }

    public synchronized boolean submissionsOpen() {
        ObjectNode event = read();

        String open =
                event.path("submissions_open").asText("");

        String close =
                event.path("submissions_close").asText("");

        if (open.isBlank() || close.isBlank()) {
            return false;
        }

        Instant now = Instant.now();

        Instant openAt = Instant.parse(open);
        Instant closeAt = Instant.parse(close);

        return !now.isBefore(openAt)
                && now.isBefore(closeAt);
    }

    public synchronized String currentEventId() {
        return read().path("id").asText();
    }

    private void seedFromFixtures()
            throws IOException {

        ObjectNode event =
                JsonNodeFactory.instance.objectNode();

        String fixtureId =
                fixtureStore.event()
                        .path("id")
                        .asText("evt_01");

        event.put("id", fixtureId);
        event.put(
                "name",
                fixtureStore.event()
                        .path("name")
                        .asText("DOGFOOD Hackathon")
        );

        event.put(
                "submissions_open",
                "2026-02-20T18:00:00Z"
        );

        event.put(
                "submissions_close",
                fixtureStore.event()
                        .path("submissions_close")
                        .asText()
        );

        ArrayNode tracks =
                JsonNodeFactory.instance.arrayNode();

        for (JsonNode track :
                fixtureStore.tracks()) {

            tracks.add(track.deepCopy());
        }

        event.set("tracks", tracks);

        event.set(
                "prizes",
                JsonNodeFactory.instance.arrayNode()
        );

        ObjectNode root =
                JsonNodeFactory.instance.objectNode();

        root.put("version", 1);
        root.set("event", event);

        writeRoot(root);
    }

    private void validateEvent(
            JsonNode event
    ) {
        if (event == null || !event.isObject()) {
            throw new IllegalArgumentException(
                    "event must be an object"
            );
        }

        String id =
                event.path("id").asText("");

        String name =
                event.path("name").asText("");

        String open =
                event.path("submissions_open").asText("");

        String close =
                event.path("submissions_close").asText("");

        if (id.isBlank()
                || name.isBlank()
                || open.isBlank()
                || close.isBlank()) {

            throw new IllegalArgumentException(
                    "event requires id, name, submissions_open, and submissions_close"
            );
        }

        Instant openAt;
        Instant closeAt;

        try {
            openAt = Instant.parse(open);
            closeAt = Instant.parse(close);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Event dates must be ISO-8601 timestamps"
            );
        }

        if (!openAt.isBefore(closeAt)) {
            throw new IllegalArgumentException(
                    "submissions_open must be before submissions_close"
            );
        }

        JsonNode tracks =
                event.path("tracks");

        if (!tracks.isArray()
                || tracks.isEmpty()) {

            throw new IllegalArgumentException(
                    "event must contain at least one track"
            );
        }

        JsonNode prizes =
                event.path("prizes");

        if (!prizes.isArray()) {
            throw new IllegalArgumentException(
                    "prizes must be an array"
            );
        }

        for (JsonNode prize : prizes) {
            if (!prize.isObject()) {
                throw new IllegalArgumentException(
                        "Every prize must be an object"
                );
            }

            String prizeId =
                    prize.path("id").asText("");

            String prizeName =
                    prize.path("name").asText("");

            if (prizeId.isBlank()
                    || prizeName.isBlank()) {

                throw new IllegalArgumentException(
                        "Every prize requires id and name"
                );
            }
        }
    }

    private void writeRoot(
            ObjectNode root
    ) {
        try {
            String content =
                    jsonMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(root);

            Path tempPath =
                    eventPath.resolveSibling(
                            "event.json.tmp"
                    );

            Files.writeString(
                    tempPath,
                    content
            );

            try {
                Files.move(
                        tempPath,
                        eventPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (
                    java.nio.file.AtomicMoveNotSupportedException e
            ) {
                Files.move(
                        tempPath,
                        eventPath,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save event storage",
                    e
            );
        }
    }
}
