package com.dogfood.backend.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.security.RequestIdFilter;
import com.dogfood.backend.service.AssignmentStore;
import com.dogfood.backend.service.AuditStore;
import com.dogfood.backend.service.BallotStore;
import com.dogfood.backend.service.FixtureStore;
import com.dogfood.backend.service.NormalizationStore;
import com.dogfood.backend.service.RubricStore;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@RestController
public class ResultsController {

    private static final String NORMALIZATION_VERSION =
            "zscore-v1";

    private static final double EPSILON =
            1.0e-6;

    private static final int MIN_SAMPLE_SIZE =
            3;

    private final AuthService authService;
    private final FixtureStore fixtureStore;
    private final RubricStore rubricStore;
    private final BallotStore ballotStore;
    private final AssignmentStore assignmentStore;
    private final NormalizationStore normalizationStore;
    private final AuditStore auditStore;
    private final JsonMapper jsonMapper;

    public ResultsController(
            AuthService authService,
            FixtureStore fixtureStore,
            RubricStore rubricStore,
            BallotStore ballotStore,
            AssignmentStore assignmentStore,
            NormalizationStore normalizationStore,
            AuditStore auditStore,
            JsonMapper jsonMapper
    ) {
        this.authService = authService;
        this.fixtureStore = fixtureStore;
        this.rubricStore = rubricStore;
        this.ballotStore = ballotStore;
        this.assignmentStore = assignmentStore;
        this.normalizationStore = normalizationStore;
        this.auditStore = auditStore;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping("/api/results/normalize")
    public ResponseEntity<?> normalize(
            HttpServletRequest request
    ) {
        var user =
                authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        if (user.get().role()
                != AuthService.Role.ORGANIZER) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        JsonNode rubric =
                rubricStore.read();

        List<JsonNode> eligibleBallots =
                new ArrayList<>();

        for (JsonNode ballot : ballotStore.readAll()) {
            String judge =
                    ballot.path("judge").asText();

            String project =
                    ballot.path("project").asText();

            if (judge.isBlank()
                    || project.isBlank()) {
                continue;
            }

            if (!assignmentStore.isAssigned(
                    judge,
                    project
            )) {
                continue;
            }

            eligibleBallots.add(ballot);
        }

        eligibleBallots.sort(
                Comparator
                        .comparing(
                                (JsonNode b) ->
                                        b.path("judge").asText()
                        )
                        .thenComparing(
                                b -> b.path("project").asText()
                        )
        );

        Map<String, Map<String, List<Double>>>
                judgeCriterionScores =
                new LinkedHashMap<>();

        for (JsonNode ballot : eligibleBallots) {
            String judge =
                    ballot.path("judge").asText();

            Map<String, List<Double>> criterionMap =
                    judgeCriterionScores.computeIfAbsent(
                            judge,
                            ignored -> new LinkedHashMap<>()
                    );

            for (JsonNode criterion :
                    rubric.path("criteria")) {

                String criterionId =
                        criterion.path("id").asText();

                double raw =
                        ballot.path("criteria")
                                .path(criterionId)
                                .asDouble();

                criterionMap
                        .computeIfAbsent(
                                criterionId,
                                ignored ->
                                        new ArrayList<>()
                        )
                        .add(raw);
            }
        }

        Map<String, Map<String, Double>>
                means =
                new LinkedHashMap<>();

        Map<String, Map<String, Double>>
                standardDeviations =
                new LinkedHashMap<>();

        Map<String, Map<String, Boolean>>
                fallback =
                new LinkedHashMap<>();

        Map<String, List<Double>>
                zValuesByCriterion =
                new LinkedHashMap<>();

        for (Map.Entry<
                String,
                Map<String, List<Double>>
                > judgeEntry :
                judgeCriterionScores.entrySet()) {

            String judge =
                    judgeEntry.getKey();

            for (JsonNode criterion :
                    rubric.path("criteria")) {

                String criterionId =
                        criterion.path("id").asText();

                List<Double> values =
                        judgeEntry.getValue()
                                .getOrDefault(
                                        criterionId,
                                        List.of()
                                );

                double mean =
                        mean(values);

                double std =
                        populationStandardDeviation(
                                values,
                                mean
                        );

                means
                        .computeIfAbsent(
                                judge,
                                ignored ->
                                        new LinkedHashMap<>()
                        )
                        .put(
                                criterionId,
                                mean
                        );

                standardDeviations
                        .computeIfAbsent(
                                judge,
                                ignored ->
                                        new LinkedHashMap<>()
                        )
                        .put(
                                criterionId,
                                std
                        );

                boolean useRawFallback =
                        values.size()
                                < MIN_SAMPLE_SIZE;

                fallback
                        .computeIfAbsent(
                                judge,
                                ignored ->
                                        new LinkedHashMap<>()
                        )
                        .put(
                                criterionId,
                                useRawFallback
                        );

                if (!useRawFallback) {
                    for (double value : values) {
                        double z =
                                (value - mean)
                                        / Math.max(
                                                std,
                                                EPSILON
                                        );

                        zValuesByCriterion
                                .computeIfAbsent(
                                        criterionId,
                                        ignored ->
                                                new ArrayList<>()
                                )
                                .add(z);
                    }
                }
            }
        }

        Map<String, Double> minZ =
                new HashMap<>();

        Map<String, Double> maxZ =
                new HashMap<>();

        for (Map.Entry<
                String,
                List<Double>
                > entry :
                zValuesByCriterion.entrySet()) {

            if (entry.getValue().isEmpty()) {
                continue;
            }

            double min =
                    entry.getValue()
                            .stream()
                            .min(Double::compare)
                            .orElse(0.0);

            double max =
                    entry.getValue()
                            .stream()
                            .max(Double::compare)
                            .orElse(0.0);

            minZ.put(
                    entry.getKey(),
                    min
            );

            maxZ.put(
                    entry.getKey(),
                    max
            );
        }

        Map<String, ObjectNode>
                projectResults =
                new LinkedHashMap<>();

        for (JsonNode ballot :
                eligibleBallots) {

            String judge =
                    ballot.path("judge").asText();

            String project =
                    ballot.path("project").asText();

            ObjectNode projectResult =
                    projectResults.computeIfAbsent(
                            project,
                            ignored ->
                                    JsonNodeFactory
                                            .instance
                                            .objectNode()
                    );

            if (!projectResult.has("project")) {
                projectResult.put(
                        "project",
                        project
                );

                projectResult.put(
                        "title",
                        projectTitle(project)
                );

                projectResult.set(
                        "judge_scores",
                        JsonNodeFactory
                                .instance
                                .arrayNode()
                );
            }

            double rawWeighted =
                    weightedScore(
                            ballot.path("criteria"),
                            rubric
                    );

            double normalizedWeighted =
                    0.0;

            ArrayNode judgeScoreDetails =
                    (ArrayNode)
                            projectResult
                                    .withArray(
                                            "judge_scores"
                                    );

            ObjectNode judgeDetail =
                    JsonNodeFactory
                            .instance
                            .objectNode();

            judgeDetail.put(
                    "judge",
                    judge
            );

            judgeDetail.put(
                    "raw_weighted_score",
                    round(rawWeighted)
            );

            ObjectNode normalizedCriteria =
                    JsonNodeFactory
                            .instance
                            .objectNode();

            for (JsonNode criterion :
                    rubric.path("criteria")) {

                String criterionId =
                        criterion.path("id").asText();

                double maxScore =
                        criterion.path("max_score")
                                .asDouble();

                double raw =
                        ballot.path("criteria")
                                .path(criterionId)
                                .asDouble();

                boolean useRaw =
                        fallback
                                .getOrDefault(
                                        judge,
                                        Map.of()
                                )
                                .getOrDefault(
                                        criterionId,
                                        true
                                );

                double normalized;

                if (useRaw) {
                    normalized = raw;
                } else {
                    double mean =
                            means.get(judge)
                                    .get(criterionId);

                    double std =
                            standardDeviations
                                    .get(judge)
                                    .get(criterionId);

                    double z =
                            (raw - mean)
                                    / Math.max(
                                            std,
                                            EPSILON
                                    );

                    double low =
                            minZ.getOrDefault(
                                    criterionId,
                                    z
                            );

                    double high =
                            maxZ.getOrDefault(
                                    criterionId,
                                    z
                            );

                    if (Math.abs(high - low)
                            <= EPSILON) {

                        normalized = raw;

                    } else {

                        normalized =
                                ((z - low)
                                        / (high - low))
                                        * maxScore;

                        normalized =
                                Math.max(
                                        0.0,
                                        Math.min(
                                                maxScore,
                                                normalized
                                        )
                                );
                    }
                }

                normalizedCriteria.put(
                        criterionId,
                        round(normalized)
                );

                double weight =
                        criterion.path("weight")
                                .asDouble();

                normalizedWeighted +=
                        (normalized / maxScore)
                                * (weight / 100.0)
                                * 5.0;
            }

            judgeDetail.set(
                    "normalized_criteria",
                    normalizedCriteria
            );

            judgeDetail.put(
                    "normalized_weighted_score",
                    round(normalizedWeighted)
            );

            judgeDetail.put(
                    "delta",
                    round(
                            normalizedWeighted
                                    - rawWeighted
                    )
            );

            judgeScoreDetails.add(
                    judgeDetail
            );
        }

        ArrayNode finalResults =
                JsonNodeFactory
                        .instance
                        .arrayNode();

        for (JsonNode project :
                fixtureStore.projects()) {

            String projectId =
                    project.path("id").asText();

            ObjectNode projectResult =
                    projectResults.get(projectId);

            if (projectResult == null) {
                continue;
            }

            ArrayNode judgeScores =
                    (ArrayNode)
                            projectResult
                                    .withArray(
                                            "judge_scores"
                                    );

            double rawTotal = 0.0;
            double normalizedTotal = 0.0;

            for (JsonNode judgeScore :
                    judgeScores) {

                rawTotal +=
                        judgeScore
                                .path(
                                        "raw_weighted_score"
                                )
                                .asDouble();

                normalizedTotal +=
                        judgeScore
                                .path(
                                        "normalized_weighted_score"
                                )
                                .asDouble();
            }

            int judgeCount =
                    judgeScores.size();

            projectResult.put(
                    "judge_count",
                    judgeCount
            );

            projectResult.put(
                    "raw_average",
                    judgeCount == 0
                            ? 0.0
                            : round(
                                    rawTotal
                                            / judgeCount
                            )
            );

            projectResult.put(
                    "normalized_average",
                    judgeCount == 0
                            ? 0.0
                            : round(
                                    normalizedTotal
                                            / judgeCount
                            )
            );

            projectResult.put(
                    "delta",
                    judgeCount == 0
                            ? 0.0
                            : round(
                                    (normalizedTotal
                                            / judgeCount)
                                            - (rawTotal
                                            / judgeCount)
                            )
            );

            finalResults.add(
                    projectResult
            );
        }

        ObjectNode run =
                JsonNodeFactory
                        .instance
                        .objectNode();

        run.put(
                "normalization_version",
                NORMALIZATION_VERSION
        );

        run.put(
                "generated_at",
                Instant.now().toString()
        );

        run.put(
                "eligible_ballots",
                eligibleBallots.size()
        );

        run.put(
                "minimum_sample_size",
                MIN_SAMPLE_SIZE
        );

        run.put(
                "epsilon",
                EPSILON
        );

        run.put(
                "standard_deviation",
                "population"
        );

        run.put(
                "rescaling",
                "For each criterion, z-scores are linearly min-max mapped into [0, criterion.max_score]. "
                        + "When a judge has fewer than the minimum sample size, that judge/criterion "
                        + "falls back to its raw score. When the global z range collapses, the raw "
                        + "criterion score is retained."
        );

        run.put(
                "input_fingerprint_sha256",
                fingerprint(
                        eligibleBallots,
                        rubric
                )
        );

        ObjectNode parameters =
                JsonNodeFactory
                        .instance
                        .objectNode();

        for (String judge :
                new java.util.TreeSet<>(
                        judgeCriterionScores.keySet()
                )) {

            ObjectNode judgeParams =
                    JsonNodeFactory
                            .instance
                            .objectNode();

            for (JsonNode criterion :
                    rubric.path("criteria")) {

                String criterionId =
                        criterion.path("id")
                                .asText();

                List<Double> values =
                        judgeCriterionScores
                                .get(judge)
                                .getOrDefault(
                                        criterionId,
                                        List.of()
                                );

                ObjectNode stats =
                        JsonNodeFactory
                                .instance
                                .objectNode();

                stats.put(
                        "sample_size",
                        values.size()
                );

                stats.put(
                        "mean",
                        round(
                                means
                                        .get(judge)
                                        .getOrDefault(
                                                criterionId,
                                                0.0
                                        )
                        )
                );

                stats.put(
                        "stddev",
                        round(
                                standardDeviations
                                        .get(judge)
                                        .getOrDefault(
                                                criterionId,
                                                0.0
                                        )
                        )
                );

                stats.put(
                        "raw_fallback",
                        fallback
                                .getOrDefault(
                                        judge,
                                        Map.of()
                                )
                                .getOrDefault(
                                        criterionId,
                                        true
                                )
                );

                judgeParams.set(
                        criterionId,
                        stats
                );
            }

            parameters.set(
                    judge,
                    judgeParams
            );
        }

        run.set(
                "parameters",
                parameters
        );

        run.set(
                "results",
                finalResults
        );

        normalizationStore.saveRun(
                run
        );

        ObjectNode auditAfter =
                JsonNodeFactory
                        .instance
                        .objectNode();

        auditAfter.put(
                "normalization_version",
                NORMALIZATION_VERSION
        );

        auditAfter.put(
                "eligible_ballots",
                eligibleBallots.size()
        );

        auditAfter.put(
                "input_fingerprint_sha256",
                run.path(
                        "input_fingerprint_sha256"
                ).asText()
        );

        auditAfter.put(
                "epsilon",
                EPSILON
        );

        auditAfter.put(
                "minimum_sample_size",
                MIN_SAMPLE_SIZE
        );

        auditStore.append(
                user.get().id(),
                "NORMALIZATION_EXECUTED",
                "results:normalization",
                null,
                auditAfter,
                "normalization_run",
                RequestIdFilter.getRequestId(request)
        );

        return ResponseEntity.ok(run);
    }

    @GetMapping("/api/results")
    public ResponseEntity<?> latestResults(
            HttpServletRequest request
    ) {
        if (!isOrganizer(request)) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        ObjectNode latest =
                normalizationStore.latestRun();

        if (latest == null) {
            return ResponseEntity.status(404)
                    .body("No normalization run exists");
        }

        return ResponseEntity.ok(latest);
    }

    private boolean isOrganizer(
            HttpServletRequest request
    ) {
        var user =
                authService.currentUser(request);

        return user.isPresent()
                && user.get().role()
                == AuthService.Role.ORGANIZER;
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

            total +=
                    (score / maxScore)
                            * (weight / 100.0)
                            * 5.0;
        }

        return total;
    }

    private double mean(
            List<Double> values
    ) {
        if (values.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;

        for (double value : values) {
            sum += value;
        }

        return sum / values.size();
    }

    private double populationStandardDeviation(
            List<Double> values,
            double mean
    ) {
        if (values.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;

        for (double value : values) {
            double delta =
                    value - mean;

            sum += delta * delta;
        }

        return Math.sqrt(
                sum / values.size()
        );
    }

    private String projectTitle(
            String projectId
    ) {
        for (JsonNode project :
                fixtureStore.projects()) {

            if (projectId.equals(
                    project.path("id").asText()
            )) {
                return project.path("title")
                        .asText();
            }
        }

        return "";
    }

    private double round(
            double value
    ) {
        return BigDecimal
                .valueOf(value)
                .setScale(
                        4,
                        RoundingMode.HALF_UP
                )
                .doubleValue();
    }

    private String fingerprint(
            List<JsonNode> ballots,
            JsonNode rubric
    ) {
        try {
            StringBuilder source =
                    new StringBuilder();

            source.append(
                    jsonMapper.writeValueAsString(
                            rubric
                    )
            );

            for (JsonNode ballot :
                    ballots) {

                source.append("|")
                        .append(
                                ballot.path("judge")
                                        .asText()
                        )
                        .append("|")
                        .append(
                                ballot.path("project")
                                        .asText()
                        )
                        .append("|")
                        .append(
                                ballot.path("criteria")
                                        .toString()
                        );
            }

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            source.toString()
                                    .getBytes(
                                            StandardCharsets.UTF_8
                                    )
                    );

            StringBuilder hex =
                    new StringBuilder();

            for (byte b : hash) {
                hex.append(
                        String.format(
                                "%02x",
                                b
                        )
                );
            }

            return hex.toString();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not create normalization fingerprint",
                    e
            );
        }
    }
}