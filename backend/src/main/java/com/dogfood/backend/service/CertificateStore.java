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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CertificateStore {

    private final JsonMapper jsonMapper;
    private final Path certificatePath;

    public CertificateStore(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.certificatePath =
                Path.of("data/certificates.json");

        try {
            Files.createDirectories(
                    certificatePath.getParent()
            );

            if (!Files.exists(certificatePath)) {
                write(
                        newRoot()
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not initialize certificate storage",
                    e
            );
        }
    }

    public synchronized ArrayNode readAll() {
        try {
            JsonNode root =
                    jsonMapper.readTree(
                            Files.readString(certificatePath)
                    );

            if (!root.isObject()) {
                throw new IllegalStateException(
                        "certificates.json must contain an object"
                );
            }

            JsonNode certificates =
                    root.path("certificates");

            if (!certificates.isArray()) {
                throw new IllegalStateException(
                        "certificates.json must contain a certificates array"
                );
            }

            return (ArrayNode) certificates;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read certificate storage",
                    e
            );
        }
    }

    public synchronized ObjectNode issue(
            String projectId,
            String teamId,
            String teamName,
            String participantId,
            String projectTitle,
            String eventId,
            String eventName,
            String track
    ) {
        for (JsonNode existing : readAll()) {
            if (projectId.equals(
                    existing.path("project_id").asText()
            ) && participantId.equals(
                    existing.path("participant_id").asText()
            )) {
                return (ObjectNode)
                        existing.deepCopy();
            }
        }

        ObjectNode root = readRoot();
        ArrayNode certificates =
                root.withArray("certificates");

        String id =
                "cert_" + UUID.randomUUID();

        ObjectNode certificate =
                JsonNodeFactory.instance.objectNode();

        certificate.put("id", id);
        certificate.put(
                "type",
                "PARTICIPATION"
        );
        certificate.put(
                "project_id",
                projectId
        );
        certificate.put(
                "team_id",
                teamId
        );
        certificate.put(
                "team_name",
                teamName == null ? "" : teamName
        );
        certificate.put(
                "participant_id",
                participantId
        );
        certificate.put(
                "project_title",
                projectTitle == null
                        ? ""
                        : projectTitle
        );
        certificate.put(
                "event_id",
                eventId == null ? "" : eventId
        );
        certificate.put(
                "event_name",
                eventName == null ? "" : eventName
        );
        certificate.put(
                "track",
                track == null ? "" : track
        );
        certificate.put(
                "issued_at",
                Instant.now().toString()
        );
        certificate.put(
                "status",
                "VALID"
        );
        certificate.put(
                "verification_path",
                "/api/certificates/" + id
        );

        certificates.add(certificate);
        root.set("certificates", certificates);
        write(root);

        return (ObjectNode) certificate.deepCopy();
    }

    public synchronized ObjectNode find(
            String certificateId
    ) {
        for (JsonNode certificate : readAll()) {
            if (certificateId.equals(
                    certificate.path("id").asText()
            )) {
                return (ObjectNode)
                        certificate.deepCopy();
            }
        }

        return null;
    }

    public synchronized List<JsonNode> forParticipant(
            String participantId
    ) {
        List<JsonNode> result = new ArrayList<>();

        for (JsonNode certificate : readAll()) {
            if (participantId.equals(
                    certificate.path("participant_id").asText()
            )) {
                result.add(
                        certificate.deepCopy()
                );
            }
        }

        return result;
    }

    public synchronized List<JsonNode> forProject(
            String projectId
    ) {
        List<JsonNode> result = new ArrayList<>();

        for (JsonNode certificate : readAll()) {
            if (projectId.equals(
                    certificate.path("project_id").asText()
            )) {
                result.add(
                        certificate.deepCopy()
                );
            }
        }

        return result;
    }

    private ObjectNode readRoot() {
        try {
            JsonNode root =
                    jsonMapper.readTree(
                            Files.readString(certificatePath)
                    );

            if (!root.isObject()) {
                throw new IllegalStateException(
                        "certificates.json must contain an object"
                );
            }

            return (ObjectNode) root;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read certificate storage",
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

            Path temp =
                    certificatePath.resolveSibling(
                            "certificates.json.tmp"
                    );

            Files.writeString(
                    temp,
                    content
            );

            try {
                Files.move(
                        temp,
                        certificatePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (
                    java.nio.file.AtomicMoveNotSupportedException e
            ) {
                Files.move(
                        temp,
                        certificatePath,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save certificate storage",
                    e
            );
        }
    }

    private ObjectNode newRoot() {
        ObjectNode root =
                JsonNodeFactory.instance.objectNode();

        root.put("version", 1);
        root.putArray("certificates");

        return root;
    }
}
