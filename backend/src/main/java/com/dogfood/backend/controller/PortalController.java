package com.dogfood.backend.controller;

import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.service.AssignmentStore;
import com.dogfood.backend.service.FixtureStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class PortalController {

    private final FixtureStore fixtureStore;
    private final AuthService authService;
    private final AssignmentStore assignmentStore;

    public PortalController(
            FixtureStore fixtureStore,
            AuthService authService,
            AssignmentStore assignmentStore
    ) {
        this.fixtureStore = fixtureStore;
        this.authService = authService;
        this.assignmentStore = assignmentStore;
    }

    // T1: public gallery
    @GetMapping("/projects")
    public ResponseEntity<List<JsonNode>> gallery() {
        return ResponseEntity.ok(fixtureStore.projects());
    }

    // T1: submission endpoint
    @PostMapping("/projects/new")
    public ResponseEntity<String> submit(
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        if (fixtureStore.submissionsClosed()) {
            return ResponseEntity.badRequest()
                    .body("Submissions are closed");
        }

        return ResponseEntity.status(201)
                .body("Submission accepted");
    }

    // T2: judges can only see explicitly assigned projects
    @GetMapping("/api/judge/projects")
    public ResponseEntity<?> judgeProjects(
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        AuthService.Role role = user.get().role();

        if (role != AuthService.Role.JUDGE_A
                && role != AuthService.Role.JUDGE_B) {
            return ResponseEntity.status(403)
                    .body("Judge access required");
        }

        String judgeId =
                role == AuthService.Role.JUDGE_A
                        ? "jdg_01"
                        : "jdg_02";

        Set<String> assignedProjectIds =
                assignmentStore.projectIdsForJudge(judgeId);

        List<JsonNode> assignedProjects =
                fixtureStore.projects()
                        .stream()
                        .filter(project ->
                                assignedProjectIds.contains(
                                        project.path("id").asText()
                                )
                        )
                        .collect(Collectors.toList());

        return ResponseEntity.ok(assignedProjects);
    }

    // T2: judges can only see their own scores
    @GetMapping("/api/judge/scores")
    public ResponseEntity<?> judgeScores(
            @RequestParam(required = false) String judge,
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        AuthService.Role role = user.get().role();

        if (role != AuthService.Role.JUDGE_A
                && role != AuthService.Role.JUDGE_B) {
            return ResponseEntity.status(403)
                    .body("Judge access required");
        }

        String currentJudge =
                role == AuthService.Role.JUDGE_A
                        ? "judge_a"
                        : "judge_b";

        if (judge != null && !judge.equals(currentJudge)) {
            return ResponseEntity.status(403)
                    .body("Access to another judge's scores is forbidden");
        }

        String fixtureJudgeId =
                currentJudge.equals("judge_a")
                        ? "jdg_01"
                        : "jdg_02";

        List<JsonNode> scores = fixtureStore.scores()
                .stream()
                .filter(score ->
                        fixtureJudgeId.equals(
                                score.path("judge").asText()
                        )
                )
                .collect(Collectors.toList());

        return ResponseEntity.ok(scores);
    }

    // T2: organizer-only CSV export
    @GetMapping(
            value = "/api/export.csv",
            produces = "text/csv"
    )
    public ResponseEntity<?> exportCsv(
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        if (user.get().role() != AuthService.Role.ORGANIZER) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        StringBuilder csv = new StringBuilder();

        csv.append(
                "judge,project,functionality,quality,innovation,comment\n"
        );

        for (JsonNode score : fixtureStore.scores()) {
            JsonNode criteria = score.path("criteria");

            csv.append(score.path("judge").asText())
                    .append(",")
                    .append(score.path("project").asText())
                    .append(",")
                    .append(criteria.path("functionality").asInt())
                    .append(",")
                    .append(criteria.path("quality").asInt())
                    .append(",")
                    .append(criteria.path("innovation").asInt())
                    .append(",")
                    .append(csvEscape(score.path("comment").asText()))
                    .append("\n");
        }

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"scores.csv\""
                )
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
