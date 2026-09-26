package com.dogfood.backend;

import com.dogfood.backend.controller.OrganizerJudgesController;
import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.service.AssignmentStore;
import com.dogfood.backend.service.AuditStore;
import com.dogfood.backend.service.FixtureStore;
import com.dogfood.backend.service.JudgeInvitationStore;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizerJudgesControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private FixtureStore fixtureStore;

    @Mock
    private AssignmentStore assignmentStore;

    @Mock
    private JudgeInvitationStore invitationStore;

    @Mock
    private AuditStore auditStore;

    @Mock
    private HttpServletRequest request;

    @Test
    void algorithmicAssignmentAddsMissingEligibleReview() {
        when(authService.currentUser(request))
                .thenReturn(Optional.of(
                        new AuthService.User(
                                "admin",
                                AuthService.Role.ADMIN
                        )
                ));

        when(fixtureStore.judges())
                .thenReturn(List.of(
                        judge("jdg_01", "Judge One", "trk_01"),
                        judge("jdg_02", "Judge Two", "trk_02")
                ));

        when(fixtureStore.projects())
                .thenReturn(List.of(
                        project("prj_test", "trk_01")
                ));

        ArrayNode noAssignments =
                JsonNodeFactory.instance.arrayNode();

        when(assignmentStore.readAll())
                .thenReturn(noAssignments);

        ObjectNode body =
                JsonNodeFactory.instance.objectNode();

        body.put("target_reviews_per_project", 1);
        body.put("dry_run", false);

        OrganizerJudgesController controller =
                new OrganizerJudgesController(
                        authService,
                        fixtureStore,
                        assignmentStore,
                        invitationStore,
                        auditStore
                );

        ResponseEntity<?> response =
                controller.autoAssign(body, request);

        assertEquals(200, response.getStatusCode().value());

        JsonNode result =
                (JsonNode) response.getBody();

        assertEquals(1, result.path("projects_changed").asInt());
        assertEquals(1, result.path("assignments_added").asInt());
        assertFalse(result.path("dry_run").asBoolean());

        ArgumentCaptor<Map<String, List<String>>> assignmentsCaptor =
                ArgumentCaptor.forClass(Map.class);

        ArgumentCaptor<Map<String, String>> tracksCaptor =
                ArgumentCaptor.forClass(Map.class);

        verify(assignmentStore).appendAlgorithmicAssignments(
                assignmentsCaptor.capture(),
                tracksCaptor.capture()
        );

        assertEquals(
                List.of("prj_test"),
                assignmentsCaptor.getValue().get("jdg_01")
        );

        assertEquals(
                "trk_01",
                tracksCaptor.getValue().get("prj_test")
        );
    }

    private static ObjectNode judge(
            String id,
            String name,
            String track
    ) {
        ObjectNode node =
                JsonNodeFactory.instance.objectNode();

        node.put("id", id);
        node.put("name", name);
        node.put("email", id + "@example.org");

        ArrayNode tracks =
                JsonNodeFactory.instance.arrayNode();

        tracks.add(track);

        node.set("tracks", tracks);

        return node;
    }

    private static ObjectNode project(
            String id,
            String track
    ) {
        ObjectNode node =
                JsonNodeFactory.instance.objectNode();

        node.put("id", id);
        node.put("track", track);
        node.put("title", "Test Project");

        return node;
    }
}
