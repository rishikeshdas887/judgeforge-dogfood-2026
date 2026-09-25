package com.dogfood.backend.controller;

import java.util.HashMap;
import java.util.HashSet;
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

            item.put(
                    "assigned_projects",
                    assignmentStore
                            .projectIdsForJudge(judgeId)
                            .size()
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

        if (user.get().role()
                != AuthService.Role.ORGANIZER) {
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

    private boolean isOrganizer(
            HttpServletRequest request
    ) {
        var user =
                authService.currentUser(request);

        return user.isPresent()
                && user.get().role()
                == AuthService.Role.ORGANIZER;
    }
}