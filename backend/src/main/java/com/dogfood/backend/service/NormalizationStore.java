package com.dogfood.backend.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class NormalizationStore {

    private static final Path FILE =
            Path.of("/app/data/normalization-results.json");

    private final JsonMapper jsonMapper;

    public NormalizationStore(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        initialize();
    }

    private void initialize() {
        try {
            Files.createDirectories(FILE.getParent());

            if (!Files.exists(FILE)) {
                ObjectNode root =
                        JsonNodeFactory.instance.objectNode();

                root.set(
                        "runs",
                        JsonNodeFactory.instance.arrayNode()
                );

                write(root);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not initialize normalization store",
                    e
            );
        }
    }

    public synchronized void saveRun(ObjectNode run) {
        try {
            ObjectNode root = readRoot();

            ArrayNode runs =
                    (ArrayNode) root.withArray("runs");

            runs.add(run);

            write(root);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save normalization run",
                    e
            );
        }
    }

    public synchronized ObjectNode latestRun() {
        try {
            ObjectNode root = readRoot();

            ArrayNode runs =
                    (ArrayNode) root.withArray("runs");

            if (runs.isEmpty()) {
                return null;
            }

            return (ObjectNode)
                    runs.get(runs.size() - 1);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read normalization runs",
                    e
            );
        }
    }

    private ObjectNode readRoot()
            throws IOException {

        if (!Files.exists(FILE)) {
            initialize();
        }

        return (ObjectNode)
                jsonMapper.readTree(
                        Files.readString(FILE)
                );
    }

    private void write(ObjectNode root)
            throws IOException {

        Files.writeString(
                FILE,
                jsonMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(root)
        );
    }
}
