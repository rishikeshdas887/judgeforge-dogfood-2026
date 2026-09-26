package com.dogfood.backend.controller;

import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.security.RequestIdFilter;
import com.dogfood.backend.service.AssignmentStore;
import com.dogfood.backend.service.AuditStore;
import com.dogfood.backend.service.FixtureStore;
import com.dogfood.backend.service.ProjectStore;
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
    private final ProjectStore projectStore;
    private final AuditStore auditStore;

    public PortalController(
            FixtureStore fixtureStore,
            AuthService authService,
            AssignmentStore assignmentStore,
            ProjectStore projectStore,
            AuditStore auditStore
    ) {
        this.fixtureStore = fixtureStore;
        this.authService = authService;
        this.assignmentStore = assignmentStore;
        this.projectStore = projectStore;
        this.auditStore = auditStore;
    }

    @GetMapping("/projects")
    public ResponseEntity<List<JsonNode>> gallery(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String track
    ) {
        return ResponseEntity.ok(
                projectStore.publicSubmitted(search, track)
        );
    }

    @PostMapping("/projects/new")
    public ResponseEntity<?> submit(
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        if (user.get().role() != AuthService.Role.PARTICIPANT) {
            return ResponseEntity.status(403)
                    .body("Participant access required");
        }

        try {
            JsonNode input =
                    body == null
                            ? tools.jackson.databind.node.JsonNodeFactory
                                    .instance.objectNode()
                            : body;

            var draft =
                    projectStore.createDraft(
                            input,
                            user.get().id()
                    );

            var submitted =
                    projectStore.submit(
                            draft.path("id").asText(),
                            user.get().id()
                    );

            auditStore.append(
                    user.get().id(),
                    "PROJECT_SUBMITTED",
                    "project:" + submitted.path("id").asText(),
                    draft,
                    submitted,
                    "project_submitted",
                    RequestIdFilter.getRequestId(request)
            );

            return ResponseEntity.status(201)
                    .body(submitted);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

    @PostMapping("/api/projects")
    public ResponseEntity<?> createDraft(
            @RequestBody JsonNode body,
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (!isParticipant(user)) {
            return participantResponse(user);
        }

        try {
            var created =
                    projectStore.createDraft(
                            body,
                            user.get().id()
                    );

            auditStore.append(
                    user.get().id(),
                    "PROJECT_DRAFT_CREATED",
                    "project:" + created.path("id").asText(),
                    null,
                    created,
                    "project_draft_created",
                    RequestIdFilter.getRequestId(request)
            );

            return ResponseEntity.status(201)
                    .body(created);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

    @GetMapping("/api/projects/mine")
    public ResponseEntity<?> myProjects(
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (!isParticipant(user)) {
            return participantResponse(user);
        }

        return ResponseEntity.ok(
                projectStore.projectsForUser(
                        user.get().id()
                )
        );
    }

    @PutMapping("/api/projects/{projectId}")
    public ResponseEntity<?> updateDraft(
            @PathVariable String projectId,
            @RequestBody JsonNode body,
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (!isParticipant(user)) {
            return participantResponse(user);
        }

        try {
            var before =
                    projectStore.find(projectId);

            if (before == null) {
                return ResponseEntity.notFound()
                        .build();
            }

            var updated =
                    projectStore.updateDraft(
                            projectId,
                            body,
                            user.get().id()
                    );

            auditStore.append(
                    user.get().id(),
                    "PROJECT_DRAFT_EDITED",
                    "project:" + projectId,
                    before,
                    updated,
                    "project_draft_edited",
                    RequestIdFilter.getRequestId(request)
            );

            return ResponseEntity.ok(updated);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/submit")
    public ResponseEntity<?> submitDraft(
            @PathVariable String projectId,
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (!isParticipant(user)) {
            return participantResponse(user);
        }

        try {
            var before =
                    projectStore.find(projectId);

            if (before == null) {
                return ResponseEntity.notFound()
                        .build();
            }

            var submitted =
                    projectStore.submit(
                            projectId,
                            user.get().id()
                    );

            auditStore.append(
                    user.get().id(),
                    "PROJECT_SUBMITTED",
                    "project:" + projectId,
                    before,
                    submitted,
                    "project_submitted",
                    RequestIdFilter.getRequestId(request)
            );

            return ResponseEntity.ok(submitted);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

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
                    .body(
                            "Access to another judge's scores is forbidden"
                    );
        }

        String fixtureJudgeId =
                currentJudge.equals("judge_a")
                        ? "jdg_01"
                        : "jdg_02";

        List<JsonNode> scores =
                fixtureStore.scores()
                        .stream()
                        .filter(score ->
                                fixtureJudgeId.equals(
                                        score.path("judge").asText()
                                )
                        )
                        .collect(Collectors.toList());

        return ResponseEntity.ok(scores);
    }

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
                .contentType(
                        MediaType.parseMediaType("text/csv")
                )
                .body(csv.toString());
    }

    private boolean isParticipant(
            java.util.Optional<AuthService.User> user
    ) {
        return user.isPresent()
                && user.get().role()
                == AuthService.Role.PARTICIPANT;
    }

    private ResponseEntity<?> participantResponse(
            java.util.Optional<AuthService.User> user
    ) {
        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        return ResponseEntity.status(403)
                .body("Participant access required");
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
