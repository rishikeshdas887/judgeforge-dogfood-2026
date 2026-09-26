package com.dogfood.backend.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class BallotStore {

    private final JsonMapper jsonMapper;
    private final FixtureStore fixtureStore;
    private final Path ballotPath;

    public BallotStore(
            JsonMapper jsonMapper,
            FixtureStore fixtureStore
    ) {
        this.jsonMapper = jsonMapper;
        this.fixtureStore = fixtureStore;
        this.ballotPath = Path.of("data/ballots.json");

        try {
            Files.createDirectories(ballotPath.getParent());

            if (!Files.exists(ballotPath)) {
                seedFromFixtures();
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not initialize ballot storage", e
            );
        }
    }

    public synchronized ArrayNode readAll() {
        try {
            JsonNode root = jsonMapper.readTree(
                    Files.readString(ballotPath)
            );

            if (!root.isArray()) {
                throw new IllegalStateException(
                        "ballots.json must contain an array"
                );
            }

            return (ArrayNode) root;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read ballot storage", e
            );
        }
    }

    public synchronized void upsert(
            String judge,
            String project,
            JsonNode criteria,
            String comment
    ) {
        ArrayNode ballots = readAll();

        for (int i = 0; i < ballots.size(); i++) {
            JsonNode ballot = ballots.get(i);

            if (judge.equals(ballot.path("judge").asText())
                    && project.equals(ballot.path("project").asText())) {

                ObjectNode updated =
                        (ObjectNode) ballot.deepCopy();

                updated.set("criteria", criteria.deepCopy());
                updated.put("comment", comment);

                ballots.set(i, updated);
                write(ballots);
                return;
            }
        }

        ObjectNode ballot = jsonMapper.createObjectNode();
        ballot.put("judge", judge);
        ballot.put("project", project);
        ballot.set("criteria", criteria.deepCopy());
        ballot.put("comment", comment);

        ballots.add(ballot);
        write(ballots);
    }

    private void seedFromFixtures() throws IOException {
        ArrayNode ballots = jsonMapper.createArrayNode();

        for (JsonNode score : fixtureStore.scores()) {
            ballots.add(score.deepCopy());
        }

        write(ballots);
    }

    private void write(ArrayNode ballots) {
        try {
            String content = jsonMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(ballots);

            Path tempPath =
                    ballotPath.resolveSibling("ballots.json.tmp");

            Files.writeString(tempPath, content);

            try {
                Files.move(
                        tempPath,
                        ballotPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                );
            } catch (
                    java.nio.file.AtomicMoveNotSupportedException e
            ) {
                Files.move(
                        tempPath,
                        ballotPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save ballot storage", e
            );
        }
    }
}
