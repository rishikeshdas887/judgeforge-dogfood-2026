package com.dogfood.backend.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class RubricStore {

    private final JsonMapper jsonMapper;
    private final Path rubricPath;

    public RubricStore(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.rubricPath = Path.of("data/rubric.json");

        try {
            Files.createDirectories(rubricPath.getParent());

            if (!Files.exists(rubricPath)) {
                throw new IllegalStateException(
                        "Missing rubric configuration: " + rubricPath
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not initialize rubric storage", e
            );
        }
    }

    public synchronized JsonNode read() {
        try {
            return jsonMapper.readTree(
                    Files.readString(rubricPath)
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read rubric configuration", e
            );
        }
    }

    public synchronized void write(JsonNode rubric) {
        try {
            String content = jsonMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(rubric);

            Path tempPath = rubricPath.resolveSibling(
                    "rubric.json.tmp"
            );

            Files.writeString(tempPath, content);

            try {
                Files.move(
                        tempPath,
                        rubricPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(
                        tempPath,
                        rubricPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save rubric configuration", e
            );
        }
    }
}
