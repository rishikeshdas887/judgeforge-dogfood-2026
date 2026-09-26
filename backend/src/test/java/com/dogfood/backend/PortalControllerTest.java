package com.dogfood.backend;

import com.dogfood.backend.controller.PortalController;
import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.service.AssignmentStore;
import com.dogfood.backend.service.AuditStore;
import com.dogfood.backend.service.FixtureStore;
import com.dogfood.backend.service.ProjectStore;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PortalControllerTest {

    private final FixtureStore fixtureStore =
            Mockito.mock(FixtureStore.class);

    private final AuthService authService =
            Mockito.mock(AuthService.class);

    private final AssignmentStore assignmentStore =
            Mockito.mock(AssignmentStore.class);

    private final ProjectStore projectStore =
            Mockito.mock(ProjectStore.class);

    private final AuditStore auditStore =
            Mockito.mock(AuditStore.class);

    private final PortalController controller =
            new PortalController(
                    fixtureStore,
                    authService,
                    assignmentStore,
                    projectStore,
                    auditStore
            );

    private final HttpServletRequest request =
            Mockito.mock(HttpServletRequest.class);

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @Test
    void nonParticipantCannotCreateDraft() {
        Mockito.when(
                authService.currentUser(request)
        ).thenReturn(
                java.util.Optional.of(
                        new AuthService.User(
                                "judge_a",
                                AuthService.Role.JUDGE_A
                        )
                )
        );

        ObjectNode body =
                objectMapper.createObjectNode();

        var response =
                controller.createDraft(
                        body,
                        request
                );

        assertEquals(
                403,
                response.getStatusCode().value()
        );

        Mockito.verifyNoInteractions(projectStore);
    }

    @Test
    void participantCanCreateDraft() {
        Mockito.when(
                authService.currentUser(request)
        ).thenReturn(
                java.util.Optional.of(
                        new AuthService.User(
                                "participant",
                                AuthService.Role.PARTICIPANT
                        )
                )
        );

        ObjectNode body =
                objectMapper.createObjectNode();

        body.put("team", "tm_test");
        body.put("track", "trk_01");
        body.put("name", "Test Project");

        ObjectNode created =
                objectMapper.createObjectNode();

        created.put("id", "prj_test");
        created.put("status", "DRAFT");

        Mockito.when(
                projectStore.createDraft(
                        body,
                        "participant"
                )
        ).thenReturn(created);

        var response =
                controller.createDraft(
                        body,
                        request
                );

        assertEquals(
                201,
                response.getStatusCode().value()
        );
        assertNotNull(response.getBody());

        Mockito.verify(projectStore)
                .createDraft(
                        body,
                        "participant"
                );
    }

    @Test
    void participantCanSubmitDraft() {
        Mockito.when(
                authService.currentUser(request)
        ).thenReturn(
                java.util.Optional.of(
                        new AuthService.User(
                                "participant",
                                AuthService.Role.PARTICIPANT
                        )
                )
        );

        ObjectNode before =
                objectMapper.createObjectNode();

        before.put("id", "prj_test");
        before.put("status", "DRAFT");

        ObjectNode submitted =
                objectMapper.createObjectNode();

        submitted.put("id", "prj_test");
        submitted.put("status", "SUBMITTED");

        Mockito.when(
                projectStore.find("prj_test")
        ).thenReturn(before);

        Mockito.when(
                projectStore.submit(
                        "prj_test",
                        "participant"
                )
        ).thenReturn(submitted);

        var response =
                controller.submitDraft(
                        "prj_test",
                        request
                );

        assertEquals(
                200,
                response.getStatusCode().value()
        );
        assertNotNull(response.getBody());

        Mockito.verify(projectStore)
                .submit(
                        "prj_test",
                        "participant"
                );
    }

    @Test
    void missingDraftReturnsNotFound() {
        Mockito.when(
                authService.currentUser(request)
        ).thenReturn(
                java.util.Optional.of(
                        new AuthService.User(
                                "participant",
                                AuthService.Role.PARTICIPANT
                        )
                )
        );

        Mockito.when(
                projectStore.find("missing")
        ).thenReturn(null);

        var response =
                controller.submitDraft(
                        "missing",
                        request
                );

        assertEquals(
                404,
                response.getStatusCode().value()
        );

        Mockito.verify(
                projectStore,
                Mockito.never()
        ).submit(
                Mockito.anyString(),
                Mockito.anyString()
        );
    }
}
