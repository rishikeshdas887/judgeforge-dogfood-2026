package com.dogfood.backend.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;

@Service
public class JudgeRecordService {

    private final BallotStore ballotStore;
    private final AssignmentStore assignmentStore;
    private final RubricStore rubricStore;
    private final JudgeRecordStore recordStore;
    private final JudgeRecordSigningService signingService;

    public JudgeRecordService(
            BallotStore ballotStore,
            AssignmentStore assignmentStore,
            RubricStore rubricStore,
            JudgeRecordStore recordStore,
            JudgeRecordSigningService signingService
    ) {
        this.ballotStore = ballotStore;
        this.assignmentStore = assignmentStore;
        this.rubricStore = rubricStore;
        this.recordStore = recordStore;
        this.signingService = signingService;
    }

    public ArrayNode snapshot() {
        ArrayNode result =
                JsonNodeFactory.instance.arrayNode();

        JsonNode rubric =
                rubricStore.read();

        for (JsonNode ballot :
                ballotStore.readAll()) {

            String judge =
                    ballot.path("judge").asText("");

            String project =
                    ballot.path("project").asText("");

            if (judge.isBlank()
                    || project.isBlank()
                    || !assignmentStore.isAssigned(
                            judge,
                            project
                    )) {
                continue;
            }

            ObjectNode payload =
                    JsonNodeFactory.instance.objectNode();

            payload.put(
                    "id",
                    stableRecordId(
                            judge,
                            project
                    )
            );
            payload.put(
                    "type",
                    "JUDGE_BALLOT"
            );
            payload.put(
                    "judge",
                    judge
            );
            payload.put(
                    "project",
                    project
            );
            payload.set(
                    "criteria",
                    ballot.path(
                            "criteria"
                    ).deepCopy()
            );
            payload.put(
                    "comment",
                    ballot.path("comment").asText("")
            );
            payload.put(
                    "weighted_score",
                    weightedScore(
                            ballot.path("criteria"),
                            rubric
                    )
            );
            payload.put(
                    "issued_at",
                    Instant.now().toString()
            );

            ObjectNode signed =
                    signingService.sign(payload);

            recordStore.upsert(signed);
            result.add(signed);
        }

        return result;
    }

    public ObjectNode get(String id) {
        return recordStore.find(id);
    }

    public ObjectNode verify(String id) {
        ObjectNode record =
                recordStore.find(id);

        if (record == null) {
            return null;
        }

        return signingService.verification(record);
    }

    public ObjectNode publicKey() {
        return signingService.publicKey();
    }

    public List<JsonNode> all() {
        return recordStore.readAll();
    }

    private double weightedScore(
            JsonNode criteria,
            JsonNode rubric
    ) {
        double total = 0.0;

        for (JsonNode criterion :
                rubric.path("criteria")) {

            String id =
                    criterion.path("id").asText();

            double score =
                    criteria.path(id)
                            .asDouble();

            double maxScore =
                    criterion.path("max_score")
                            .asDouble();

            double weight =
                    criterion.path("weight")
                            .asDouble();

            if (maxScore <= 0.0) {
                continue;
            }

            total +=
                    (score / maxScore)
                            * (weight / 100.0);
        }

        return total;
    }

    private String stableRecordId(
            String judge,
            String project
    ) {
        String source =
                judge + ":" + project;

        try {
            byte[] digest =
                    java.security.MessageDigest
                            .getInstance("SHA-256")
                            .digest(
                                    source.getBytes(
                                            java.nio.charset.StandardCharsets.UTF_8
                                    )
                            );

            StringBuilder result =
                    new StringBuilder();

            for (byte b : digest) {
                result.append(
                        String.format("%02x", b)
                );
            }

            return "jrec_" +
                    result.substring(0, 24);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not create judge record id",
                    e
            );
        }
    }
}
