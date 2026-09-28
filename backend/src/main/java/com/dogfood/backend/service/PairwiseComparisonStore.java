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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PairwiseComparisonStore {

    private final JsonMapper jsonMapper;
    private final Path comparisonPath;

    public PairwiseComparisonStore(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.comparisonPath = Path.of("data/pairwise-comparisons.json");

        try {
            Files.createDirectories(comparisonPath.getParent());

            if (!Files.exists(comparisonPath)) {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("version", 1);
                root.set(
                        "comparisons",
                        JsonNodeFactory.instance.arrayNode()
                );
                write(root);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not initialize pairwise comparison storage",
                    e
            );
        }
    }

    public synchronized ArrayNode readAll() {
        try {
            JsonNode root = jsonMapper.readTree(
                    Files.readString(comparisonPath)
            );

            JsonNode comparisons = root.path("comparisons");

            if (!root.isObject() || !comparisons.isArray()) {
                throw new IllegalStateException(
                        "pairwise-comparisons.json must contain a comparisons array"
                );
            }

            return (ArrayNode) comparisons;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read pairwise comparisons",
                    e
            );
        }
    }

    public synchronized List<JsonNode> comparisonsForJudge(
            String judgeId
    ) {
        List<JsonNode> result = new ArrayList<>();

        for (JsonNode comparison : readAll()) {
            if (judgeId.equals(
                    comparison.path("judge").asText()
            )) {
                result.add(comparison.deepCopy());
            }
        }

        return result;
    }

    public synchronized boolean hasComparedPair(
            String judgeId,
            String projectA,
            String projectB
    ) {
        String pairKey = canonicalPair(projectA, projectB);

        for (JsonNode comparison : readAll()) {
            if (!judgeId.equals(
                    comparison.path("judge").asText()
            )) {
                continue;
            }

            String existingPair = canonicalPair(
                    comparison.path("project_a").asText(),
                    comparison.path("project_b").asText()
            );

            if (pairKey.equals(existingPair)) {
                return true;
            }
        }

        return false;
    }

    public synchronized ObjectNode append(
            String judgeId,
            String projectA,
            String projectB,
            String winner
    ) {
        if (judgeId == null || judgeId.isBlank()) {
            throw new IllegalArgumentException("Judge is required");
        }

        if (projectA == null
                || projectB == null
                || projectA.isBlank()
                || projectB.isBlank()) {

            throw new IllegalArgumentException(
                    "Both projects are required"
            );
        }

        if (projectA.equals(projectB)) {
            throw new IllegalArgumentException(
                    "A project cannot be compared against itself"
            );
        }

        if (!projectA.equals(winner)
                && !projectB.equals(winner)) {

            throw new IllegalArgumentException(
                    "Winner must be one of the submitted projects"
            );
        }

        if (hasComparedPair(
                judgeId,
                projectA,
                projectB
        )) {
            throw new IllegalStateException(
                    "This project pair was already compared by this judge"
            );
        }

        ObjectNode comparison =
                JsonNodeFactory.instance.objectNode();

        comparison.put(
                "id",
                "cmp_" + UUID.randomUUID()
        );

        comparison.put(
                "judge",
                judgeId
        );

        comparison.put(
                "project_a",
                projectA
        );

        comparison.put(
                "project_b",
                projectB
        );

        comparison.put(
                "winner",
                winner
        );

        comparison.put(
                "loser",
                projectA.equals(winner)
                        ? projectB
                        : projectA
        );

        comparison.put(
                "created_at",
                Instant.now().toString()
        );

        ObjectNode root = readRoot();

        root.withArray("comparisons")
                .add(comparison);

        write(root);

        return (ObjectNode) comparison.deepCopy();
    }

    private String canonicalPair(
            String projectA,
            String projectB
    ) {
        if (projectA.compareTo(projectB) <= 0) {
            return projectA + "::" + projectB;
        }

        return projectB + "::" + projectA;
    }

    private ObjectNode readRoot() {
        try {
            JsonNode root = jsonMapper.readTree(
                    Files.readString(comparisonPath)
            );

            if (!root.isObject()) {
                throw new IllegalStateException(
                        "pairwise-comparisons.json must contain an object"
                );
            }

            return (ObjectNode) root;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read pairwise comparison storage",
                    e
            );
        }
    }

    private void write(ObjectNode root) {
        try {
            String content =
                    jsonMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(root);

            Path tempPath =
                    comparisonPath.resolveSibling(
                            "pairwise-comparisons.json.tmp"
                    );

            Files.writeString(
                    tempPath,
                    content
            );

            try {
                Files.move(
                        tempPath,
                        comparisonPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                );
            } catch (
                    java.nio.file.AtomicMoveNotSupportedException e
            ) {
                Files.move(
                        tempPath,
                        comparisonPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save pairwise comparisons",
                    e
            );
        }
    }
}
