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
import java.util.ArrayList;
import java.util.List;

@Service
public class JudgeRecordStore {

    private final JsonMapper jsonMapper;
    private final Path path;

    public JudgeRecordStore(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.path = Path.of("data/judge-records.json");

        try {
            Files.createDirectories(path.getParent());

            if (!Files.exists(path)) {
                writeRoot(newRoot());
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not initialize judge record storage",
                    e
            );
        }
    }

    public synchronized ObjectNode find(String id) {
        for (JsonNode record : readAll()) {
            if (id.equals(record.path("id").asText())) {
                return (ObjectNode) record.deepCopy();
            }
        }

        return null;
    }

    public synchronized void upsert(ObjectNode record) {
        ObjectNode root = readRoot();
        ArrayNode records =
                root.withArray("records");

        for (int i = 0; i < records.size(); i++) {
            if (record.path("id").asText().equals(
                    records.get(i).path("id").asText()
            )) {
                records.set(i, record.deepCopy());
                writeRoot(root);
                return;
            }
        }

        records.add(record.deepCopy());
        writeRoot(root);
    }

    public synchronized List<JsonNode> readAll() {
        ObjectNode root = readRoot();
        List<JsonNode> result = new ArrayList<>();

        for (JsonNode record :
                root.withArray("records")) {
            result.add(record.deepCopy());
        }

        return result;
    }

    private ObjectNode readRoot() {
        try {
            JsonNode root =
                    jsonMapper.readTree(
                            Files.readString(path)
                    );

            if (!root.isObject()) {
                throw new IllegalStateException(
                        "judge-records.json must contain an object"
                );
            }

            return (ObjectNode) root;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read judge record storage",
                    e
            );
        }
    }

    private void writeRoot(ObjectNode root) {
        try {
            String content =
                    jsonMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(root);

            Path temp =
                    path.resolveSibling(
                            "judge-records.json.tmp"
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
                    "Could not save judge record storage",
                    e
            );
        }
    }

    private ObjectNode newRoot() {
        ObjectNode root =
                JsonNodeFactory.instance.objectNode();

        root.put("version", 1);
        root.putArray("records");

        return root;
    }
}
