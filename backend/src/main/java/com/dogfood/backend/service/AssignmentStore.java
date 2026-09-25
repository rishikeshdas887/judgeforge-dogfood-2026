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
import java.util.HashSet;
import java.util.Set;

@Service
public class AssignmentStore {

    private final JsonMapper jsonMapper;
    private final Path assignmentPath;

    public AssignmentStore(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.assignmentPath =
                Path.of("/app/data/assignments.json");

        if (!Files.exists(assignmentPath)) {
            throw new IllegalStateException(
                    "Missing assignment configuration: "
                            + assignmentPath
            );
        }
    }

    public synchronized ArrayNode readAll() {
        try {
            JsonNode root = jsonMapper.readTree(
                    Files.readString(assignmentPath)
            );

            JsonNode assignments =
                    root.path("assignments");

            if (!assignments.isArray()) {
                throw new IllegalStateException(
                        "assignments.json must contain an assignments array"
                );
            }

            return (ArrayNode) assignments;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read assignments", e
            );
        }
    }

    public synchronized Set<String> projectIdsForJudge(
            String judgeId
    ) {
        Set<String> projects = new HashSet<>();

        for (JsonNode assignment : readAll()) {
            if (judgeId.equals(
                    assignment.path("judge").asText())
                    && "ACTIVE".equals(
                    assignment.path("status").asText())) {

                projects.add(
                        assignment.path("project").asText()
                );
            }
        }

        return projects;
    }

    public synchronized boolean isAssigned(
            String judgeId,
            String projectId
    ) {
        for (JsonNode assignment : readAll()) {
            if (judgeId.equals(
                    assignment.path("judge").asText())
                    && projectId.equals(
                    assignment.path("project").asText())
                    && "ACTIVE".equals(
                    assignment.path("status").asText())) {

                return true;
            }
        }

        return false;
    }

    public synchronized void replaceJudgeAssignments(
            String judgeId,
            ArrayNode projectIds,
            java.util.Map<String, String> projectTracks
    ) {
        ArrayNode existing = readAll();
        ArrayNode updated =
                JsonNodeFactory.instance.arrayNode();

        Set<String> requested =
                new HashSet<>();

        for (JsonNode projectIdNode : projectIds) {
            requested.add(projectIdNode.asText());
        }

        for (JsonNode assignment : existing) {
            if (!judgeId.equals(
                    assignment.path("judge").asText())) {

                updated.add(assignment.deepCopy());
            }
        }

        for (String projectId : requested) {
            String track = projectTracks.get(projectId);

            if (track == null) {
                throw new IllegalArgumentException(
                        "Unknown project: " + projectId
                );
            }

            ObjectNode assignment =
                    JsonNodeFactory.instance.objectNode();

            assignment.put(
                    "id",
                    "asg_" + judgeId + "_" + projectId
            );
            assignment.put("judge", judgeId);
            assignment.put("project", projectId);
            assignment.put("track", track);
            assignment.put("status", "ACTIVE");
            assignment.put("source", "organizer_assignment");

            updated.add(assignment);
        }

        ObjectNode root =
                JsonNodeFactory.instance.objectNode();

        root.put(
                "version",
                readVersion(existing)
        );
        root.set("assignments", updated);

        write(root);
    }

    private int readVersion(ArrayNode ignored) {
        try {
            JsonNode root = jsonMapper.readTree(
                    Files.readString(assignmentPath)
            );
            return root.path("version").asInt(1);
        } catch (IOException e) {
            return 1;
        }
    }

    private void write(ObjectNode root) {
        try {
            String content =
                    jsonMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(root);

            Path tempPath =
                    assignmentPath.resolveSibling(
                            "assignments.json.tmp"
                    );

            Files.writeString(tempPath, content);

            try {
                Files.move(
                        tempPath,
                        assignmentPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                );
            } catch (
                    java.nio.file.AtomicMoveNotSupportedException e) {

                Files.move(
                        tempPath,
                        assignmentPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save assignments", e
            );
        }
    }
}
