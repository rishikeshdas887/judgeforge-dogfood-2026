package com.dogfood.backend.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.security.RequestIdFilter;
import com.dogfood.backend.service.AssignmentStore;
import com.dogfood.backend.service.AuditStore;
import com.dogfood.backend.service.FixtureStore;
import com.dogfood.backend.service.JudgeInvitationStore;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@RestController
public class OrganizerJudgesController {

    private final AuthService authService;
    private final FixtureStore fixtureStore;
    private final AssignmentStore assignmentStore;
    private final JudgeInvitationStore invitationStore;
    private final AuditStore auditStore;

    public OrganizerJudgesController(
            AuthService authService,
            FixtureStore fixtureStore,
            AssignmentStore assignmentStore,
            JudgeInvitationStore invitationStore,
            AuditStore auditStore
    ) {
        this.authService = authService;
        this.fixtureStore = fixtureStore;
        this.assignmentStore = assignmentStore;
        this.invitationStore = invitationStore;
        this.auditStore = auditStore;
    }

    @GetMapping("/api/organizer/judges")
    public ResponseEntity<?> judges(
            HttpServletRequest request
    ) {
        if (!isOrganizer(request)) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        ArrayNode result =
                JsonNodeFactory.instance.arrayNode();

        Map<String, JsonNode> invitations =
                new HashMap<>();

        for (JsonNode invitation :
                invitationStore.readAll()) {

            invitations.put(
                    invitation.path("judge").asText(),
                    invitation
            );
        }

        for (JsonNode judge :
                fixtureStore.judges()) {

            String judgeId =
                    judge.path("id").asText();

            ObjectNode item =
                    JsonNodeFactory.instance.objectNode();

            item.put("judge", judgeId);
            item.put(
                    "name",
                    judge.path("name").asText()
            );
            item.put(
                    "email",
                    judge.path("email").asText()
            );

            JsonNode invitation =
                    invitations.get(judgeId);

            item.put(
                    "invitation_status",
                    invitation == null
                            ? "UNKNOWN"
                            : invitation.path("status").asText()
            );

            java.util.List<String> assignedProjectIds =
                    assignmentStore
                            .projectIdsForJudge(judgeId)
                            .stream()
                            .sorted()
                            .toList();

            item.put(
                    "assigned_projects",
                    assignedProjectIds.size()
            );

            ArrayNode assignedProjects =
                    JsonNodeFactory.instance.arrayNode();

            assignedProjectIds.forEach(assignedProjects::add);

            item.set(
                    "assigned_project_ids",
                    assignedProjects
            );

            ArrayNode tracks =
                    JsonNodeFactory.instance.arrayNode();

            judge.path("tracks")
                    .forEach(tracks::add);

            item.set("allowed_tracks", tracks);

            result.add(item);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping(
            "/api/organizer/judges/{judgeId}/invite"
    )
    public ResponseEntity<?> invite(
            @PathVariable String judgeId,
            HttpServletRequest request
    ) {
        if (!isOrganizer(request)) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        boolean exists =
                fixtureStore.judges()
                        .stream()
                        .anyMatch(j ->
                                judgeId.equals(
                                        j.path("id").asText()
                                )
                        );

        if (!exists) {
            return ResponseEntity.notFound()
                    .build();
        }

        return ResponseEntity.ok(
                invitationStore.invite(judgeId)
        );
    }

    @PutMapping(
            "/api/organizer/judges/{judgeId}/assignments"
    )
    public ResponseEntity<?> assign(
            @PathVariable String judgeId,
            @RequestBody JsonNode body,
            HttpServletRequest request
    ) {
        var user =
                authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        if (!AuthService.isOrganizerOrAdmin(user.get().role())) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        JsonNode judge =
                fixtureStore.judges()
                        .stream()
                        .filter(j ->
                                judgeId.equals(
                                        j.path("id").asText()
                                )
                        )
                        .findFirst()
                        .orElse(null);

        if (judge == null) {
            return ResponseEntity.notFound()
                    .build();
        }

        JsonNode projectIds =
                body.path("project_ids");

        if (!projectIds.isArray()) {
            return ResponseEntity.badRequest()
                    .body(
                            "project_ids must be an array"
                    );
        }

        Set<String> allowedTracks =
                new HashSet<>();

        judge.path("tracks").forEach(track ->
                allowedTracks.add(track.asText())
        );

        Map<String, String> projectTracks =
                new HashMap<>();

        for (JsonNode project :
                fixtureStore.projects()) {

            projectTracks.put(
                    project.path("id").asText(),
                    project.path("track").asText()
            );
        }

        for (JsonNode projectIdNode : projectIds) {
            String projectId =
                    projectIdNode.asText();

            String track =
                    projectTracks.get(projectId);

            if (track == null) {
                return ResponseEntity.badRequest()
                        .body(
                                "Unknown project: "
                                        + projectId
                        );
            }

            if (!allowedTracks.contains(track)) {
                return ResponseEntity.badRequest()
                        .body(
                                "Project "
                                        + projectId
                                        + " is outside the judge's allowed tracks"
                        );
            }
        }

        Set<String> beforeProjects =
                assignmentStore.projectIdsForJudge(judgeId);

        try {
            assignmentStore.replaceJudgeAssignments(
                    judgeId,
                    (ArrayNode) projectIds,
                    projectTracks
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }

        Set<String> afterProjects =
                assignmentStore.projectIdsForJudge(judgeId);

        ObjectNode before =
                JsonNodeFactory.instance.objectNode();

        ArrayNode beforeProjectIds =
                JsonNodeFactory.instance.arrayNode();

        beforeProjects.stream()
                .sorted()
                .forEach(beforeProjectIds::add);

        before.set(
                "project_ids",
                beforeProjectIds
        );

        ObjectNode after =
                JsonNodeFactory.instance.objectNode();

        ArrayNode afterProjectIds =
                JsonNodeFactory.instance.arrayNode();

        afterProjects.stream()
                .sorted()
                .forEach(afterProjectIds::add);

        after.set(
                "project_ids",
                afterProjectIds
        );

        auditStore.append(
                user.get().id(),
                "JUDGE_ASSIGNED",
                "judge:" + judgeId,
                before,
                after,
                "assignment_updated",
                RequestIdFilter.getRequestId(request)
        );

        ObjectNode result =
                JsonNodeFactory.instance.objectNode();

        result.put("judge", judgeId);
        result.set(
                "project_ids",
                projectIds.deepCopy()
        );
        result.put(
                "assignment_count",
                projectIds.size()
        );

        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/organizer/judges/assignments/batch")
    public ResponseEntity<?> batchAssign(
            @RequestBody JsonNode body,
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

        JsonNode assignmentsNode = body.path("assignments");

        if (!assignmentsNode.isArray()
                || assignmentsNode.size() == 0) {
            return ResponseEntity.badRequest()
                    .body("assignments must be a non-empty array");
        }

        Map<String, JsonNode> judges = new HashMap<>();

        for (JsonNode judge : fixtureStore.judges()) {
            judges.put(
                    judge.path("id").asText(),
                    judge
            );
        }

        Map<String, String> projectTracks = new HashMap<>();

        for (JsonNode project : fixtureStore.projects()) {
            projectTracks.put(
                    project.path("id").asText(),
                    project.path("track").asText()
            );
        }

        Map<String, ArrayNode> validated = new HashMap<>();

        for (JsonNode item : assignmentsNode) {
            String judgeId =
                    item.path("judge_id").asText("").trim();

            if (judgeId.isBlank()) {
                return ResponseEntity.badRequest()
                        .body("judge_id is required");
            }

            if (validated.containsKey(judgeId)) {
                return ResponseEntity.badRequest()
                        .body("Duplicate judge_id: " + judgeId);
            }

            JsonNode judge = judges.get(judgeId);

            if (judge == null) {
                return ResponseEntity.badRequest()
                        .body("Unknown judge: " + judgeId);
            }

            JsonNode projectIdsNode =
                    item.path("project_ids");

            if (!projectIdsNode.isArray()) {
                return ResponseEntity.badRequest()
                        .body(
                                "project_ids must be an array for "
                                        + judgeId
                        );
            }

            Set<String> allowedTracks = new HashSet<>();

            judge.path("tracks").forEach(track ->
                    allowedTracks.add(track.asText())
            );

            Set<String> seenProjects = new HashSet<>();
            ArrayNode normalizedProjectIds =
                    JsonNodeFactory.instance.arrayNode();

            for (JsonNode projectIdNode : projectIdsNode) {
                String projectId =
                        projectIdNode.asText("").trim();

                if (projectId.isBlank()) {
                    return ResponseEntity.badRequest()
                            .body(
                                    "project_ids cannot contain blank values"
                            );
                }

                if (!seenProjects.add(projectId)) {
                    return ResponseEntity.badRequest()
                            .body(
                                    "Duplicate project "
                                            + projectId
                                            + " for judge "
                                            + judgeId
                            );
                }

                String track = projectTracks.get(projectId);

                if (track == null) {
                    return ResponseEntity.badRequest()
                            .body(
                                    "Unknown project: " + projectId
                            );
                }

                if (!allowedTracks.contains(track)) {
                    return ResponseEntity.badRequest()
                            .body(
                                    "Project "
                                            + projectId
                                            + " is outside the judge's allowed tracks"
                            );
                }

                normalizedProjectIds.add(projectId);
            }

            validated.put(
                    judgeId,
                    normalizedProjectIds
            );
        }

        ArrayNode changes =
                JsonNodeFactory.instance.arrayNode();

        for (Map.Entry<String, ArrayNode> entry :
                validated.entrySet()) {

            String judgeId = entry.getKey();

            ObjectNode change =
                    JsonNodeFactory.instance.objectNode();

            change.put("judge", judgeId);
            change.put(
                    "assignment_count",
                    entry.getValue().size()
            );

            changes.add(change);
        }

        ObjectNode result =
                JsonNodeFactory.instance.objectNode();

        result.set("changes", changes);
        result.put(
                "judge_count",
                validated.size()
        );

        if (body.path("dry_run").asBoolean(false)) {
            result.put("dry_run", true);
            return ResponseEntity.ok(result);
        }

        for (Map.Entry<String, ArrayNode> entry :
                validated.entrySet()) {

            assignmentStore.replaceJudgeAssignments(
                    entry.getKey(),
                    entry.getValue(),
                    projectTracks
            );
        }

        auditStore.append(
                user.get().id(),
                "BATCH_ASSIGNMENT_UPDATED",
                "judges:batch",
                null,
                result,
                "batch_assignment",
                RequestIdFilter.getRequestId(request)
        );

        result.put("dry_run", false);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/organizer/judges/assignments/auto")
    public ResponseEntity<?> autoAssign(
            @RequestBody JsonNode body,
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

        int targetReviews =
                body.path("target_reviews_per_project").asInt(2);

        if (targetReviews < 1
                || targetReviews > fixtureStore.judges().size()) {
            return ResponseEntity.badRequest()
                    .body(
                            "target_reviews_per_project must be between 1 and "
                                    + fixtureStore.judges().size()
                    );
        }

        boolean dryRun =
                body.path("dry_run").asBoolean(true);

        Map<String, JsonNode> judges =
                new LinkedHashMap<>();

        Map<String, Integer> judgeLoad =
                new LinkedHashMap<>();

        for (JsonNode judge : fixtureStore.judges()) {
            String judgeId =
                    judge.path("id").asText();

            judges.put(judgeId, judge);
            judgeLoad.put(judgeId, 0);
        }

        Map<String, String> projectTracks =
                new HashMap<>();

        List<JsonNode> projects =
                new ArrayList<>();

        for (JsonNode project :
                fixtureStore.projects()) {

            projects.add(project);

            projectTracks.put(
                    project.path("id").asText(),
                    project.path("track").asText()
            );
        }

        projects.sort(
                Comparator.comparing(
                        p -> p.path("id").asText()
                )
        );

        Map<String, Set<String>> currentByProject =
                new HashMap<>();

        for (JsonNode assignment :
                assignmentStore.readAll()) {

            if (!"ACTIVE".equals(
                    assignment.path("status").asText())) {
                continue;
            }

            String judgeId =
                    assignment.path("judge").asText();

            String projectId =
                    assignment.path("project").asText();

            if (!judgeLoad.containsKey(judgeId)) {
                continue;
            }

            judgeLoad.put(
                    judgeId,
                    judgeLoad.get(judgeId) + 1
            );

            currentByProject
                    .computeIfAbsent(
                            projectId,
                            ignored -> new HashSet<>()
                    )
                    .add(judgeId);
        }

        Map<String, List<String>> proposedByJudge =
                new LinkedHashMap<>();

        int projectsChanged = 0;
        int assignmentsAdded = 0;

        for (JsonNode project : projects) {
            String projectId =
                    project.path("id").asText();

            String track =
                    project.path("track").asText();

            Set<String> assignedJudges =
                    new HashSet<>(
                            currentByProject.getOrDefault(
                                    projectId,
                                    Set.of()
                            )
                    );

            int needed =
                    targetReviews - assignedJudges.size();

            if (needed <= 0) {
                continue;
            }

            List<String> eligibleJudges =
                    new ArrayList<>();

            for (Map.Entry<String, JsonNode> entry :
                    judges.entrySet()) {

                String judgeId = entry.getKey();

                if (assignedJudges.contains(judgeId)) {
                    continue;
                }

                boolean allowed =
                        false;

                for (JsonNode allowedTrack :
                        entry.getValue().path("tracks")) {

                    if (track.equals(
                            allowedTrack.asText())) {
                        allowed = true;
                        break;
                    }
                }

                if (allowed) {
                    eligibleJudges.add(judgeId);
                }
            }

            if (eligibleJudges.size() < needed) {
                return ResponseEntity.badRequest()
                        .body(
                                "Not enough eligible judges for "
                                        + projectId
                                        + ": need "
                                        + needed
                                        + " more, have "
                                        + eligibleJudges.size()
                        );
            }

            for (int i = 0; i < needed; i++) {
                String selected =
                        eligibleJudges.stream()
                                .filter(
                                        candidate ->
                                                !assignedJudges.contains(
                                                        candidate
                                                )
                                )
                                .min(
                                        Comparator
                                                .comparingInt(
                                                        (String candidate) ->
                                                                judgeLoad.get(candidate)
                                                )
                                                .thenComparing(
                                                        Comparator.naturalOrder()
                                                )
                                )
                                .orElseThrow();

                assignedJudges.add(selected);

                proposedByJudge
                        .computeIfAbsent(
                                selected,
                                ignored -> new ArrayList<>()
                        )
                        .add(projectId);

                judgeLoad.put(
                        selected,
                        judgeLoad.get(selected) + 1
                );

                assignmentsAdded++;
            }

            projectsChanged++;
        }

        ArrayNode changes =
                JsonNodeFactory.instance.arrayNode();

        for (Map.Entry<String, List<String>> entry :
                proposedByJudge.entrySet()) {

            ObjectNode change =
                    JsonNodeFactory.instance.objectNode();

            change.put(
                    "judge",
                    entry.getKey()
            );

            ArrayNode projectIds =
                    JsonNodeFactory.instance.arrayNode();

            entry.getValue().forEach(projectIds::add);

            change.set(
                    "project_ids",
                    projectIds
            );

            change.put(
                    "assignment_count",
                    entry.getValue().size()
            );

            changes.add(change);
        }

        ObjectNode result =
                JsonNodeFactory.instance.objectNode();

        result.put(
                "target_reviews_per_project",
                targetReviews
        );
        result.put(
                "projects_changed",
                projectsChanged
        );
        result.put(
                "assignments_added",
                assignmentsAdded
        );
        result.set("changes", changes);
        result.put("dry_run", dryRun);

        if (dryRun) {
            return ResponseEntity.ok(result);
        }

        assignmentStore.appendAlgorithmicAssignments(
                proposedByJudge,
                projectTracks
        );

        auditStore.append(
                user.get().id(),
                "ALGORITHMIC_ASSIGNMENT_UPDATED",
                "judges:algorithmic",
                null,
                result,
                "algorithmic_assignment",
                RequestIdFilter.getRequestId(request)
        );

        return ResponseEntity.ok(result);
    }

    private boolean isOrganizer(
            HttpServletRequest request
    ) {
        var user =
                authService.currentUser(request);

        return user.isPresent()
                && AuthService.isOrganizerOrAdmin(user.get().role());
    }
}
