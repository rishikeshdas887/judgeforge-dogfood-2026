package com.dogfood.backend.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.security.RequestIdFilter;
import com.dogfood.backend.service.AssignmentStore;
import com.dogfood.backend.service.AuditStore;
import com.dogfood.backend.service.BallotStore;
import com.dogfood.backend.service.RubricStore;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@RestController
public class JudgingController {

    private final AuthService authService;
    private final RubricStore rubricStore;
    private final BallotStore ballotStore;
    private final AssignmentStore assignmentStore;
    private final AuditStore auditStore;

    public JudgingController(
            AuthService authService,
            RubricStore rubricStore,
            BallotStore ballotStore,
            AssignmentStore assignmentStore,
            AuditStore auditStore
    ) {
        this.authService = authService;
        this.rubricStore = rubricStore;
        this.ballotStore = ballotStore;
        this.assignmentStore = assignmentStore;
        this.auditStore = auditStore;
    }

    @GetMapping("/api/judge/rubric")
    public ResponseEntity<?> rubric(
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        String judgeId = fixtureJudgeId(user.get().role());

        if (judgeId == null) {
            return ResponseEntity.status(403)
                    .body("Judge access required");
        }

        return ResponseEntity.ok(rubricStore.read());
    }

    @GetMapping("/api/judge/ballots")
    public ResponseEntity<?> ballots(
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        String judgeId = fixtureJudgeId(user.get().role());

        if (judgeId == null) {
            return ResponseEntity.status(403)
                    .body("Judge access required");
        }

        ArrayNode result =
                tools.jackson.databind.node.JsonNodeFactory
                        .instance
                        .arrayNode();

        for (JsonNode ballot : ballotStore.readAll()) {
            if (!judgeId.equals(
                    ballot.path("judge").asText())) {
                continue;
            }

            if (!isAssignedProject(
                    judgeId,
                    ballot.path("project").asText()
            )) {
                continue;
            }

            ObjectNode view =
                    (ObjectNode) ballot.deepCopy();

            view.put(
                    "weighted_score",
                    weightedScore(
                            ballot.path("criteria"),
                            rubricStore.read()
                    )
            );

            result.add(view);
        }

        return ResponseEntity.ok(result);
    }

    @PutMapping("/api/judge/ballots/{projectId}")
    public ResponseEntity<?> saveBallot(
            @PathVariable String projectId,
            @RequestBody JsonNode body,
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        String judgeId = fixtureJudgeId(user.get().role());

        if (judgeId == null) {
            return ResponseEntity.status(403)
                    .body("Judge access required");
        }

        if (!isAssignedProject(judgeId, projectId)) {

    auditStore.append(
            user.get().id(),
            "BALLOT_ACCESS_DENIED",
            "ballot:" + judgeId + ":" + projectId,
            null,
            null,
            "judge_assignment_denied",
            RequestIdFilter.getRequestId(request)
    );

    return ResponseEntity.status(403)
            .body("Project is not assigned to this judge");
}

        JsonNode rubric = rubricStore.read();
        JsonNode criteria = body.path("criteria");

        String validationError =
                validateCriteria(criteria, rubric);

        if (validationError != null) {
            return ResponseEntity.badRequest()
                    .body(validationError);
        }

        String comment = body.path("comment").asText("");

        JsonNode previousBallot = null;

        for (JsonNode ballot : ballotStore.readAll()) {
            if (judgeId.equals(
                    ballot.path("judge").asText())
                    && projectId.equals(
                    ballot.path("project").asText())) {

                previousBallot = ballot.deepCopy();
                break;
            }
        }

        String auditAction =
                previousBallot == null
                        ? "BALLOT_SUBMITTED"
                        : "BALLOT_EDITED";

        String auditReason =
                previousBallot == null
                        ? "initial_submission"
                        : "judge_edit";

        ballotStore.upsert(
                judgeId,
                projectId,
                criteria,
                comment
        );

        for (JsonNode ballot : ballotStore.readAll()) {
            if (judgeId.equals(
                    ballot.path("judge").asText())
                    && projectId.equals(
                    ballot.path("project").asText())) {

                ObjectNode result =
                        (ObjectNode) ballot.deepCopy();

                result.put(
                        "weighted_score",
                        weightedScore(
                                ballot.path("criteria"),
                                rubric
                        )
                );

                auditStore.append(
                        user.get().id(),
                        auditAction,
                        "ballot:" + judgeId + ":" + projectId,
                        previousBallot,
                        ballot,
                        auditReason,
                        RequestIdFilter.getRequestId(request)
                );

                return ResponseEntity.ok(result);
            }
        }

        return ResponseEntity.internalServerError()
                .body("Ballot was not saved");
    }

    private String fixtureJudgeId(AuthService.Role role) {
        if (role == AuthService.Role.JUDGE_A) {
            return "jdg_01";
        }

        if (role == AuthService.Role.JUDGE_B) {
            return "jdg_02";
        }

        return null;
    }

    private boolean isAssignedProject(
            String judgeId,
            String projectId
    ) {
        return assignmentStore.isAssigned(
                judgeId,
                projectId
        );
    }

    private String validateCriteria(
            JsonNode criteria,
            JsonNode rubric
    ) {
        if (criteria == null || !criteria.isObject()) {
            return "criteria must be an object";
        }

        JsonNode configuredCriteria =
                rubric.path("criteria");

        for (JsonNode criterion : configuredCriteria) {
            String id = criterion.path("id").asText();

            JsonNode value = criteria.path(id);

            if (!value.isNumber()) {
                return "Missing numeric score: " + id;
            }

            double score = value.asDouble();
            double maxScore =
                    criterion.path("max_score").asDouble();

            if (score < 0 || score > maxScore) {
                return "Score out of bounds: " + id;
            }
        }

        return null;
    }

    private double weightedScore(
            JsonNode criteria,
            JsonNode rubric
    ) {
        double total = 0.0;

        for (JsonNode criterion : rubric.path("criteria")) {
            String id = criterion.path("id").asText();

            double score =
                    criteria.path(id).asDouble();

            double maxScore =
                    criterion.path("max_score").asDouble();

            double weight =
                    criterion.path("weight").asDouble();

            total +=
                    (score / maxScore)
                    * (weight / 100.0)
                    * 5.0;
        }

        return BigDecimal
                .valueOf(total)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
