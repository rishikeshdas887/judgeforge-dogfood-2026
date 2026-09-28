package com.dogfood.backend.controller;

import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.security.RequestIdFilter;
import com.dogfood.backend.service.AssignmentStore;
import com.dogfood.backend.service.AuditStore;
import com.dogfood.backend.service.BradleyTerryEstimator;
import com.dogfood.backend.service.PairwiseComparisonStore;
import com.dogfood.backend.service.ProjectStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/judge/pairwise")
public class PairwiseJudgingController {

    private final AuthService authService;
    private final AssignmentStore assignmentStore;
    private final ProjectStore projectStore;
    private final PairwiseComparisonStore comparisonStore;
    private final BradleyTerryEstimator estimator;
    private final AuditStore auditStore;

    public PairwiseJudgingController(
            AuthService authService,
            AssignmentStore assignmentStore,
            ProjectStore projectStore,
            PairwiseComparisonStore comparisonStore,
            BradleyTerryEstimator estimator,
            AuditStore auditStore
    ) {
        this.authService = authService;
        this.assignmentStore = assignmentStore;
        this.projectStore = projectStore;
        this.comparisonStore = comparisonStore;
        this.estimator = estimator;
        this.auditStore = auditStore;
    }

    @GetMapping("/next")
    public ResponseEntity<?> next(
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        String judgeId =
                fixtureJudgeId(user.get().role());

        if (judgeId == null) {
            return ResponseEntity.status(403)
                    .body("Judge access required");
        }

        List<JsonNode> projects =
                assignedSubmittedProjects(judgeId);

        int completed =
                comparisonStore
                        .comparisonsForJudge(judgeId)
                        .size();

        int possible =
                projects.size()
                        * Math.max(projects.size() - 1, 0)
                        / 2;

        for (int i = 0; i < projects.size(); i++) {
            for (int j = i + 1; j < projects.size(); j++) {

                String projectA =
                        projects.get(i).path("id").asText();

                String projectB =
                        projects.get(j).path("id").asText();

                if (comparisonStore.hasComparedPair(
                        judgeId,
                        projectA,
                        projectB
                )) {
                    continue;
                }

                ObjectNode response =
                        JsonNodeFactory.instance.objectNode();

                response.put("complete", false);
                response.put("completed", completed);
                response.put("possible", possible);
                response.put(
                        "remaining",
                        Math.max(possible - completed, 0)
                );

                response.set(
                        "project_a",
                        projectView(projects.get(i))
                );

                response.set(
                        "project_b",
                        projectView(projects.get(j))
                );

                return ResponseEntity.ok(response);
            }
        }

        ObjectNode response =
                JsonNodeFactory.instance.objectNode();

        response.put("complete", true);
        response.put("completed", completed);
        response.put("possible", possible);
        response.put("remaining", 0);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/progress")
    public ResponseEntity<?> progress(
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        String judgeId =
                fixtureJudgeId(user.get().role());

        if (judgeId == null) {
            return ResponseEntity.status(403)
                    .body("Judge access required");
        }

        int projectCount =
                assignedSubmittedProjects(judgeId).size();

        int possible =
                projectCount
                        * Math.max(projectCount - 1, 0)
                        / 2;

        int completed =
                comparisonStore
                        .comparisonsForJudge(judgeId)
                        .size();

        ObjectNode response =
                JsonNodeFactory.instance.objectNode();

        response.put(
                "judge",
                judgeId
        );

        response.put(
                "projects",
                projectCount
        );

        response.put(
                "completed",
                completed
        );

        response.put(
                "possible",
                possible
        );

        response.put(
                "remaining",
                Math.max(possible - completed, 0)
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/comparisons")
    public ResponseEntity<?> submit(
            @RequestBody JsonNode body,
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        String judgeId =
                fixtureJudgeId(user.get().role());

        if (judgeId == null) {
            return ResponseEntity.status(403)
                    .body("Judge access required");
        }

        String projectA =
                body.path("project_a").asText("");

        String projectB =
                body.path("project_b").asText("");

        String winner =
                body.path("winner").asText("");

        if (projectA.isBlank()
                || projectB.isBlank()
                || winner.isBlank()) {

            return ResponseEntity.badRequest()
                    .body(
                            "project_a, project_b and winner are required"
                    );
        }

        if (projectA.equals(projectB)) {
            return ResponseEntity.badRequest()
                    .body(
                            "project_a and project_b must be different"
                    );
        }

        if (!projectA.equals(winner)
                && !projectB.equals(winner)) {

            return ResponseEntity.badRequest()
                    .body(
                            "winner must be project_a or project_b"
                    );
        }

        if (!assignmentStore.isAssigned(
                judgeId,
                projectA
        ) || !assignmentStore.isAssigned(
                judgeId,
                projectB
        )) {

            auditStore.append(
                    user.get().id(),
                    "PAIRWISE_ACCESS_DENIED",
                    "pairwise:" + judgeId
                            + ":" + projectA
                            + ":" + projectB,
                    null,
                    body,
                    "project_assignment_denied",
                    RequestIdFilter.getRequestId(request)
            );

            return ResponseEntity.status(403)
                    .body(
                            "Both projects must be assigned to this judge"
                    );
        }

        JsonNode projectAData =
                projectStore.find(projectA);

        JsonNode projectBData =
                projectStore.find(projectB);

        if (!isSubmitted(projectAData)
                || !isSubmitted(projectBData)) {

            return ResponseEntity.badRequest()
                    .body(
                            "Only submitted projects can be compared"
                    );
        }

        try {
            ObjectNode saved =
                    comparisonStore.append(
                            judgeId,
                            projectA,
                            projectB,
                            winner
                    );

            auditStore.append(
                    user.get().id(),
                    "PAIRWISE_COMPARISON_SUBMITTED",
                    "pairwise:" + judgeId
                            + ":" + projectA
                            + ":" + projectB,
                    null,
                    saved,
                    "judge_pairwise_vote",
                    RequestIdFilter.getRequestId(request)
            );

            return ResponseEntity.status(201)
                    .body(saved);

        } catch (IllegalStateException e) {

            auditStore.append(
                    user.get().id(),
                    "PAIRWISE_COMPARISON_DUPLICATE",
                    "pairwise:" + judgeId
                            + ":" + projectA
                            + ":" + projectB,
                    null,
                    body,
                    "duplicate_pair",
                    RequestIdFilter.getRequestId(request)
            );

            return ResponseEntity.status(409)
                    .body(e.getMessage());
        }
    }

    @GetMapping("/results")
    public ResponseEntity<?> results(
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        if (!AuthService.isOrganizerOrAdmin(
                user.get().role()
        )) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        List<JsonNode> projects =
                projectStore.publicSubmitted(
                        null,
                        null
                );

        Map<String, JsonNode> projectMap =
                new HashMap<>();

        for (JsonNode project : projects) {
            projectMap.put(
                    project.path("id").asText(),
                    project
            );
        }

        Set<String> projectIds =
                new HashSet<>(projectMap.keySet());

      List<JsonNode> comparisons =
        new ArrayList<>();
       for (JsonNode comparison :
        comparisonStore.readAll()) {
    comparisons.add(comparison);
}

        ArrayNode ranking =
                estimator.rank(
                        comparisons,
                        projectIds
                );

        for (JsonNode row : ranking) {
            ObjectNode ranked =
                    (ObjectNode) row;

            String projectId =
                    ranked.path("project_id").asText();

            JsonNode project =
                    projectMap.get(projectId);

            if (project != null) {
                ranked.put(
                        "title",
                        project.path("title").asText()
                );

                ranked.put(
                        "track",
                        project.path("track").asText()
                );
            }
        }

        boolean connected =
                isConnected(
                        projectIds,
                        comparisons
                );

        ObjectNode response =
                JsonNodeFactory.instance.objectNode();

        response.put(
                "estimator",
                "bradley-terry-v1"
        );

        response.put(
                "regularization",
                0.01
        );

        response.put(
                "projects",
                projectIds.size()
        );

        response.put(
                "comparisons",
                comparisons.size()
        );

        response.put(
                "comparison_graph_connected",
                connected
        );

        response.put(
                "ranking_status",
                connected
                        ? "CONNECTED"
                        : "PRELIMINARY_DISCONNECTED"
        );

        response.set(
                "ranking",
                ranking
        );

        return ResponseEntity.ok(response);
    }

    private List<JsonNode> assignedSubmittedProjects(
            String judgeId
    ) {
        Set<String> assigned =
                assignmentStore.projectIdsForJudge(
                        judgeId
                );

        List<JsonNode> projects =
                new ArrayList<>();

        for (JsonNode project :
                projectStore.publicSubmitted(
                        null,
                        null
                )) {

            String projectId =
                    project.path("id").asText();

            if (assigned.contains(projectId)) {
                projects.add(project);
            }
        }

        projects.sort(
                Comparator.comparing(
                        project ->
                                project.path("id")
                                        .asText()
                )
        );

        return projects;
    }

    private ObjectNode projectView(
            JsonNode project
    ) {
        ObjectNode view =
                JsonNodeFactory.instance.objectNode();

        view.put(
                "id",
                project.path("id").asText()
        );

        view.put(
                "title",
                project.path("title").asText()
        );

        view.put(
                "tagline",
                project.path("tagline").asText()
        );

        view.put(
                "track",
                project.path("track").asText()
        );

        view.put(
                "summary",
                project.path("summary").asText()
        );

        view.put(
                "thumbnail",
                project.path("thumbnail").asText()
        );

        view.set(
                "tech_tags",
                project.path("tech_tags").deepCopy()
        );

        view.put(
                "repository_url",
                project.path("repository_url").asText()
        );

        return view;
    }

    private boolean isSubmitted(
            JsonNode project
    ) {
        return project != null
                && "SUBMITTED".equals(
                project.path("status").asText()
        );
    }

    private String fixtureJudgeId(
            AuthService.Role role
    ) {
        if (role == AuthService.Role.JUDGE_A) {
            return "jdg_01";
        }

        if (role == AuthService.Role.JUDGE_B) {
            return "jdg_02";
        }

        return null;
    }

    private boolean isConnected(
            Set<String> projectIds,
            List<JsonNode> comparisons
    ) {
        if (projectIds.size() <= 1) {
            return true;
        }

        Map<String, Set<String>> graph =
                new HashMap<>();

        for (String projectId : projectIds) {
            graph.put(
                    projectId,
                    new HashSet<>()
            );
        }

        for (JsonNode comparison : comparisons) {
            String a =
                    comparison.path("project_a")
                            .asText();

            String b =
                    comparison.path("project_b")
                            .asText();

            if (!graph.containsKey(a)
                    || !graph.containsKey(b)) {
                continue;
            }

            graph.get(a).add(b);
            graph.get(b).add(a);
        }

        String start =
                projectIds.iterator().next();

        Set<String> visited =
                new HashSet<>();

        ArrayDeque<String> queue =
                new ArrayDeque<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String current =
                    queue.removeFirst();

            for (String next :
                    graph.get(current)) {

                if (visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }

        return visited.size()
                == projectIds.size();
    }
}
