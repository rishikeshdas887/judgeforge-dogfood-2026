package com.dogfood.backend.controller;

import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.service.AssignmentStore;
import com.dogfood.backend.service.BallotStore;
import com.dogfood.backend.service.FixtureStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@RestController
public class OrganizerJudgingController {

    private final AuthService authService;
    private final FixtureStore fixtureStore;
    private final BallotStore ballotStore;
    private final AssignmentStore assignmentStore;

    public OrganizerJudgingController(
            AuthService authService,
            FixtureStore fixtureStore,
            BallotStore ballotStore,
            AssignmentStore assignmentStore
    ) {
        this.authService = authService;
        this.fixtureStore = fixtureStore;
        this.ballotStore = ballotStore;
        this.assignmentStore = assignmentStore;
    }

    @GetMapping("/api/organizer/judging-progress")
    public ResponseEntity<?> judgingProgress(
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        if (!AuthService.isOrganizerOrAdmin(user.get().role())) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        Set<String> completedAssignments = new HashSet<>();

        for (JsonNode ballot : ballotStore.readAll()) {
            String judgeId = ballot.path("judge").asText();
            String projectId = ballot.path("project").asText();

            if (!judgeId.isBlank()
                    && !projectId.isBlank()) {

                completedAssignments.add(
                        judgeId + "::" + projectId
                );
            }
        }

        ArrayNode judges =
                JsonNodeFactory.instance.arrayNode();

        int totalAssigned = 0;
        int totalCompleted = 0;

        for (JsonNode judge : fixtureStore.judges()) {
            String judgeId = judge.path("id").asText();

            if (judgeId.isBlank()) {
                continue;
            }

            Set<String> assignedProjects = new HashSet<>();
            Set<String> assignedTracks = new HashSet<>();

            for (JsonNode assignment :
                    assignmentStore.readAll()) {

                if (!judgeId.equals(
                        assignment.path("judge").asText())) {
                    continue;
                }

                if (!"ACTIVE".equals(
                        assignment.path("status").asText())) {
                    continue;
                }

                String projectId =
                        assignment.path("project").asText();

                String track =
                        assignment.path("track").asText();

                if (!projectId.isBlank()) {
                    assignedProjects.add(projectId);
                }

                if (!track.isBlank()) {
                    assignedTracks.add(track);
                }
            }

            int assigned = assignedProjects.size();
            int completed = 0;

            for (String projectId : assignedProjects) {
                if (completedAssignments.contains(
                        judgeId + "::" + projectId
                )) {
                    completed++;
                }
            }

            int pending = assigned - completed;

            double completion =
                    assigned == 0
                            ? 0.0
                            : ((double) completed / assigned)
                            * 100.0;

            ObjectNode judgeProgress =
                    JsonNodeFactory.instance.objectNode();

            judgeProgress.put("judge", judgeId);
            judgeProgress.put(
                    "name",
                    judge.path("name").asText()
            );
            judgeProgress.put(
                    "assigned_reviews",
                    assigned
            );
            judgeProgress.put(
                    "completed_reviews",
                    completed
            );
            judgeProgress.put(
                    "pending_reviews",
                    pending
            );
            judgeProgress.put(
                    "completion_percent",
                    BigDecimal.valueOf(completion)
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            )
                            .doubleValue()
            );

            ArrayNode tracks =
                    JsonNodeFactory.instance.arrayNode();

            assignedTracks.stream()
                    .sorted()
                    .forEach(tracks::add);

            judgeProgress.set("tracks", tracks);

            judges.add(judgeProgress);

            totalAssigned += assigned;
            totalCompleted += completed;
        }

        int totalPending =
                totalAssigned - totalCompleted;

        double overallCompletion =
                totalAssigned == 0
                        ? 0.0
                        : ((double) totalCompleted / totalAssigned)
                        * 100.0;

        ObjectNode response =
                JsonNodeFactory.instance.objectNode();

        response.put(
                "generated_at",
                Instant.now().toString()
        );
        response.put(
                "total_assigned_reviews",
                totalAssigned
        );
        response.put(
                "total_completed_reviews",
                totalCompleted
        );
        response.put(
                "total_pending_reviews",
                totalPending
        );
        response.put(
                "overall_completion_percent",
                BigDecimal.valueOf(overallCompletion)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        )
                        .doubleValue()
        );
        response.set("judges", judges);

        return ResponseEntity.ok(response);
    }
}
