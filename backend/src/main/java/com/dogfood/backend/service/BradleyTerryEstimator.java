package com.dogfood.backend.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BradleyTerryEstimator {

    private static final int MAX_ITERATIONS = 2000;
    private static final double LEARNING_RATE = 0.05;
    private static final double REGULARIZATION = 0.01;
    private static final double CONVERGENCE_TOLERANCE = 1e-8;

    public ArrayNode rank(
            List<JsonNode> comparisons,
            Set<String> projectIds
    ) {
        Map<String, Double> strength =
                new HashMap<>();

        Map<String, Integer> wins =
                new HashMap<>();

        Map<String, Integer> losses =
                new HashMap<>();

        Map<String, Integer> comparisonCounts =
                new HashMap<>();

        for (String projectId : projectIds) {
            strength.put(projectId, 0.0);
            wins.put(projectId, 0);
            losses.put(projectId, 0);
            comparisonCounts.put(projectId, 0);
        }

        List<JsonNode> validComparisons =
                new ArrayList<>();

        for (JsonNode comparison : comparisons) {
            String projectA =
                    comparison.path("project_a").asText("");

            String projectB =
                    comparison.path("project_b").asText("");

            String winner =
                    comparison.path("winner").asText("");

            if (!strength.containsKey(projectA)
                    || !strength.containsKey(projectB)
                    || projectA.equals(projectB)
                    || (!projectA.equals(winner)
                    && !projectB.equals(winner))) {

                continue;
            }

            validComparisons.add(comparison);

            wins.merge(
                    winner,
                    1,
                    Integer::sum
            );

            String loser =
                    projectA.equals(winner)
                            ? projectB
                            : projectA;

            losses.merge(
                    loser,
                    1,
                    Integer::sum
            );

            comparisonCounts.merge(
                    projectA,
                    1,
                    Integer::sum
            );

            comparisonCounts.merge(
                    projectB,
                    1,
                    Integer::sum
            );
        }

        for (int iteration = 0;
             iteration < MAX_ITERATIONS;
             iteration++) {

            Map<String, Double> gradient =
                    new HashMap<>();

            for (String projectId : projectIds) {
                gradient.put(
                        projectId,
                        -REGULARIZATION
                                * strength.get(projectId)
                );
            }

            for (JsonNode comparison : validComparisons) {
                String projectA =
                        comparison.path("project_a").asText();

                String projectB =
                        comparison.path("project_b").asText();

                String winner =
                        comparison.path("winner").asText();

                String loser =
                        projectA.equals(winner)
                                ? projectB
                                : projectA;

                double winnerStrength =
                        strength.get(winner);

                double loserStrength =
                        strength.get(loser);

                double probability =
                        sigmoid(
                                winnerStrength
                                        - loserStrength
                        );

                double error =
                        1.0 - probability;

                gradient.merge(
                        winner,
                        error,
                        Double::sum
                );

                gradient.merge(
                        loser,
                        -error,
                        Double::sum
                );
            }

            double maxChange = 0.0;

            for (String projectId : projectIds) {
                double oldValue =
                        strength.get(projectId);

                double newValue =
                        oldValue
                                + LEARNING_RATE
                                * gradient.get(projectId);

                strength.put(
                        projectId,
                        newValue
                );

                maxChange = Math.max(
                        maxChange,
                        Math.abs(newValue - oldValue)
                );
            }

            center(strength);

            if (maxChange < CONVERGENCE_TOLERANCE) {
                break;
            }
        }

        List<String> orderedProjects =
                new ArrayList<>(projectIds);

        orderedProjects.sort(
                Comparator
                        .comparing(
                                (String id) ->
                                        strength.get(id)
                        )
                        .reversed()
                        .thenComparing(id -> id)
        );

        ArrayNode result =
                JsonNodeFactory.instance.arrayNode();

        double previousStrength =
                Double.NaN;

        int currentRank = 0;

        for (int index = 0;
             index < orderedProjects.size();
             index++) {

            String projectId =
                    orderedProjects.get(index);

            double projectStrength =
                    strength.get(projectId);

            if (index == 0
                    || Double.compare(
                    projectStrength,
                    previousStrength
            ) != 0) {
                currentRank = index + 1;
            }

            ObjectNode row =
                    JsonNodeFactory.instance.objectNode();

            row.put(
                    "rank",
                    currentRank
            );

            row.put(
                    "project_id",
                    projectId
            );

            row.put(
                    "strength",
                    round(projectStrength)
            );

            row.put(
                    "wins",
                    wins.get(projectId)
            );

            row.put(
                    "losses",
                    losses.get(projectId)
            );

            row.put(
                    "comparisons",
                    comparisonCounts.get(projectId)
            );

            double winRate =
                    comparisonCounts.get(projectId) == 0
                            ? 0.0
                            : (double) wins.get(projectId)
                            / comparisonCounts.get(projectId);

            row.put(
                    "win_rate",
                    round(winRate)
            );

            result.add(row);

            previousStrength =
                    projectStrength;
        }

        return result;
    }

    private double sigmoid(double value) {
        if (value >= 0.0) {
            double z = Math.exp(-value);
            return 1.0 / (1.0 + z);
        }

        double z = Math.exp(value);
        return z / (1.0 + z);
    }

    private void center(
            Map<String, Double> strength
    ) {
        if (strength.isEmpty()) {
            return;
        }

        double mean =
                strength.values()
                        .stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);

        for (String projectId :
                new HashSet<>(strength.keySet())) {

            strength.put(
                    projectId,
                    strength.get(projectId) - mean
            );
        }
    }

    private double round(double value) {
        return Math.round(
                value * 1000000.0
        ) / 1000000.0;
    }
}
