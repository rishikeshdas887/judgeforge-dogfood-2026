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
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class JudgeInvitationStore {

    private final JsonMapper jsonMapper;
    private final FixtureStore fixtureStore;
    private final Path invitationPath;
    private final SecureRandom random = new SecureRandom();

    public JudgeInvitationStore(
            JsonMapper jsonMapper,
            FixtureStore fixtureStore
    ) {
        this.jsonMapper = jsonMapper;
        this.fixtureStore = fixtureStore;
        this.invitationPath =
                Path.of("/app/data/judge-invitations.json");

        try {
            Files.createDirectories(
                    invitationPath.getParent()
            );

            if (!Files.exists(invitationPath)) {
                seed();
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not initialize judge invitations",
                    e
            );
        }
    }

    public synchronized ArrayNode readAll() {
        try {
            JsonNode root =
                    jsonMapper.readTree(
                            Files.readString(invitationPath)
                    );

            return (ArrayNode) root.path("judges");
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read judge invitations",
                    e
            );
        }
    }

    public synchronized ObjectNode invite(
            String judgeId
    ) {
        ArrayNode judges = readAll();

        for (JsonNode node : judges) {
            if (!judgeId.equals(
                    node.path("judge").asText())) {
                continue;
            }

            ObjectNode judge =
                    (ObjectNode) node.deepCopy();

            String token = generateToken();

            judge.put("status", "INVITED");
            judge.put("invited_at",
                    Instant.now().toString());
            judge.put("invite_token", token);

            replace(judges, judgeId, judge);

            ObjectNode result =
                    JsonNodeFactory.instance.objectNode();

            result.put("judge", judgeId);
            result.put("status", "INVITED");
            result.put("invite_token", token);

            return result;
        }

        throw new IllegalArgumentException(
                "Judge not found: " + judgeId
        );
    }

    private void seed() throws IOException {
        ArrayNode judges =
                JsonNodeFactory.instance.arrayNode();

        for (JsonNode judge :
                fixtureStore.judges()) {

            ObjectNode entry =
                    JsonNodeFactory.instance.objectNode();

            entry.put(
                    "judge",
                    judge.path("id").asText()
            );
            entry.put(
                    "name",
                    judge.path("name").asText()
            );
            entry.put(
                    "email",
                    judge.path("email").asText()
            );
            entry.put("status", "ACTIVE");

            judges.add(entry);
        }

        write(judges);
    }

    private String generateToken() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);

        StringBuilder result =
                new StringBuilder();

        for (byte value : bytes) {
            result.append(
                    String.format(
                            "%02x",
                            value & 0xff
                    )
            );
        }

        return result.toString();
    }

    private void replace(
            ArrayNode judges,
            String judgeId,
            ObjectNode replacement
    ) {
        for (int i = 0; i < judges.size(); i++) {
            if (judgeId.equals(
                    judges.get(i)
                            .path("judge")
                            .asText())) {

                judges.set(i, replacement);
                write(judges);
                return;
            }
        }

        throw new IllegalStateException(
                "Judge disappeared during update"
        );
    }

    private void write(ArrayNode judges) {
        try {
            ObjectNode root =
                    JsonNodeFactory.instance.objectNode();

            root.put("version", 1);
            root.set("judges", judges);

            String content =
                    jsonMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(root);

            Path temp =
                    invitationPath.resolveSibling(
                            "judge-invitations.json.tmp"
                    );

            Files.writeString(temp, content);

            try {
                Files.move(
                        temp,
                        invitationPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                );
            } catch (
                    java.nio.file.AtomicMoveNotSupportedException e) {

                Files.move(
                        temp,
                        invitationPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save judge invitations",
                    e
            );
        }
    }
}
