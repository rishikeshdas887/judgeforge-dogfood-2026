package com.dogfood.backend;

import com.dogfood.backend.controller.TeamController;
import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.service.TeamStore;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TeamControllerTest {

    private final AuthService authService =
            Mockito.mock(AuthService.class);

    private final TeamStore teamStore =
            Mockito.mock(TeamStore.class);

    private final TeamController controller =
            new TeamController(authService, teamStore);

    private final HttpServletRequest request =
            Mockito.mock(HttpServletRequest.class);

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @Test
    void nonParticipantCannotAccessTeams() {
        Mockito.when(authService.currentUser(request))
                .thenReturn(
                        java.util.Optional.of(
                                new AuthService.User(
                                        "judge_a",
                                        AuthService.Role.JUDGE_A
                                )
                        )
                );

        var response = controller.teams(request);

        assertEquals(403, response.getStatusCode().value());
        Mockito.verifyNoInteractions(teamStore);
    }

    @Test
    void participantCanCreateTeam() {
        Mockito.when(authService.currentUser(request))
                .thenReturn(
                        java.util.Optional.of(
                                new AuthService.User(
                                        "participant",
                                        AuthService.Role.PARTICIPANT
                                )
                        )
                );

        ObjectNode body =
                objectMapper.createObjectNode();

        body.put("name", "Test Team");

        ObjectNode created =
                objectMapper.createObjectNode();

        created.put("id", "tm_test");
        created.put("name", "Test Team");

        Mockito.when(
                teamStore.createTeam(
                        "Test Team",
                        "participant"
                )
        ).thenReturn(created);

        var response =
                controller.createTeam(body, request);

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());

        Mockito.verify(teamStore)
                .createTeam(
                        "Test Team",
                        "participant"
                );
    }

    @Test
    void participantCanCreateInviteForOwnTeam() {
        Mockito.when(authService.currentUser(request))
                .thenReturn(
                        java.util.Optional.of(
                                new AuthService.User(
                                        "participant",
                                        AuthService.Role.PARTICIPANT
                                )
                        )
                );

        ObjectNode invite =
                objectMapper.createObjectNode();

        invite.put("token", "test-token");
        invite.put("team", "tm_test");
        invite.put("status", "PENDING");

        Mockito.when(
                teamStore.createInvite(
                        "tm_test",
                        "participant"
                )
        ).thenReturn(invite);

        var response =
                controller.createInvite(
                        "tm_test",
                        request
                );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        Mockito.verify(teamStore)
                .createInvite(
                        "tm_test",
                        "participant"
                );
    }
}
