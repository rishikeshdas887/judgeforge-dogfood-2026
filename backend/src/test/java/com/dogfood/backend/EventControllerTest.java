package com.dogfood.backend;

import com.dogfood.backend.controller.EventController;
import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.service.AuditStore;
import com.dogfood.backend.service.EventStore;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EventControllerTest {

    private final AuthService authService =
            Mockito.mock(AuthService.class);

    private final EventStore eventStore =
            Mockito.mock(EventStore.class);

    private final AuditStore auditStore =
            Mockito.mock(AuditStore.class);

    private final EventController controller =
            new EventController(
                    authService,
                    eventStore,
                    auditStore
            );

    private final HttpServletRequest request =
            Mockito.mock(HttpServletRequest.class);

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @Test
    void nonOrganizerCannotReadEvent() {
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

        var response =
                controller.getEvent(request);

        assertEquals(
                403,
                response.getStatusCode().value()
        );

        Mockito.verifyNoInteractions(eventStore);
    }

    @Test
    void organizerCanReadEvent() {
        Mockito.when(
                authService.currentUser(request)
        ).thenReturn(
                java.util.Optional.of(
                        new AuthService.User(
                                "organizer",
                                AuthService.Role.ORGANIZER
                        )
                )
        );

        ObjectNode event =
                objectMapper.createObjectNode();

        event.put("id", "evt_test");
        event.put("name", "Test Event");

        Mockito.when(eventStore.read())
                .thenReturn(event);

        var response =
                controller.getEvent(request);

        assertEquals(
                200,
                response.getStatusCode().value()
        );

        assertNotNull(response.getBody());
        assertEquals(
                "evt_test",
                ((ObjectNode) response.getBody())
                        .path("id")
                        .asText()
        );
    }

    @Test
    void organizerRejectsInvalidEventDates() {
        Mockito.when(
                authService.currentUser(request)
        ).thenReturn(
                java.util.Optional.of(
                        new AuthService.User(
                                "organizer",
                                AuthService.Role.ORGANIZER
                        )
                )
        );

        ObjectNode event =
                objectMapper.createObjectNode();

        event.put("id", "evt_test");
        event.put("name", "Test Event");
        event.put(
                "submissions_open",
                "2026-10-02T18:00:00Z"
        );
        event.put(
                "submissions_close",
                "2026-10-01T18:00:00Z"
        );

        event.putArray("tracks")
                .addObject()
                .put("id", "trk_test")
                .put("name", "Test Track");

        event.putArray("prizes");

        Mockito.when(eventStore.write(event))
                .thenThrow(
                        new IllegalArgumentException(
                                "submissions_open must be before submissions_close"
                        )
                );

        var response =
                controller.createEvent(
                        event,
                        request
                );

        assertEquals(
                400,
                response.getStatusCode().value()
        );

        assertEquals(
                "submissions_open must be before submissions_close",
                response.getBody()
        );

        Mockito.verify(eventStore)
                .write(event);

        Mockito.verifyNoInteractions(auditStore);
    }
}
