package com.dogfood.backend;

import com.dogfood.backend.controller.JudgingController;
import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.security.RequestIdFilter;
import com.dogfood.backend.service.AssignmentStore;
import com.dogfood.backend.service.AuditStore;
import com.dogfood.backend.service.BallotStore;
import com.dogfood.backend.service.RubricStore;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgingControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private RubricStore rubricStore;

    @Mock
    private BallotStore ballotStore;

    @Mock
    private AssignmentStore assignmentStore;

    @Mock
    private AuditStore auditStore;

    @Mock
    private HttpServletRequest request;

    @Test
    void judgeCannotScoreUnassignedProjectAndAccessIsAudited() {
        when(authService.currentUser(request))
                .thenReturn(Optional.of(
                        new AuthService.User(
                                "judge_b",
                                AuthService.Role.JUDGE_B
                        )
                ));

        when(assignmentStore.isAssigned("jdg_02", "prj_41"))
                .thenReturn(false);

        when(request.getAttribute(
                RequestIdFilter.REQUEST_ID_ATTRIBUTE
        )).thenReturn("req_test_403");

        JudgingController controller =
                new JudgingController(
                        authService,
                        rubricStore,
                        ballotStore,
                        assignmentStore,
                        auditStore
                );

        ResponseEntity<?> response =
                controller.saveBallot(
                        "prj_41",
                        null,
                        request
                );

        assertEquals(403, response.getStatusCode().value());
        assertEquals(
                "Project is not assigned to this judge",
                response.getBody()
        );

        verify(auditStore).append(
                eq("judge_b"),
                eq("BALLOT_ACCESS_DENIED"),
                eq("ballot:jdg_02:prj_41"),
                eq(null),
                eq(null),
                eq("judge_assignment_denied"),
                eq("req_test_403")
        );

        verifyNoInteractions(rubricStore, ballotStore);
    }

    @Test
    void invalidScoreIsRejectedBeforeBallotPersistence() {
        when(authService.currentUser(request))
                .thenReturn(Optional.of(
                        new AuthService.User(
                                "judge_a",
                                AuthService.Role.JUDGE_A
                        )
                ));

        when(assignmentStore.isAssigned("jdg_01", "prj_07"))
                .thenReturn(true);

        when(rubricStore.read())
                .thenReturn(rubric());

        ObjectNode criteria =
                JsonNodeFactory.instance.objectNode();

        criteria.put("functionality", 6);
        criteria.put("quality", 4);
        criteria.put("innovation", 4);

        ObjectNode body =
                JsonNodeFactory.instance.objectNode();

        body.set("criteria", criteria);

        JudgingController controller =
                new JudgingController(
                        authService,
                        rubricStore,
                        ballotStore,
                        assignmentStore,
                        auditStore
                );

        ResponseEntity<?> response =
                controller.saveBallot(
                        "prj_07",
                        body,
                        request
                );

        assertEquals(400, response.getStatusCode().value());
        assertEquals(
                "Score out of bounds: functionality",
                response.getBody()
        );

        verifyNoInteractions(ballotStore, auditStore);
    }

    @Test
    void validNewBallotIsSavedAndAudited() {
        when(authService.currentUser(request))
                .thenReturn(Optional.of(
                        new AuthService.User(
                                "judge_a",
                                AuthService.Role.JUDGE_A
                        )
                ));

        when(assignmentStore.isAssigned("jdg_01", "prj_07"))
                .thenReturn(true);

        when(rubricStore.read())
                .thenReturn(rubric());

        when(request.getAttribute(
                RequestIdFilter.REQUEST_ID_ATTRIBUTE
        )).thenReturn("req_test_submit");

        ArrayNode emptyBallots =
                JsonNodeFactory.instance.arrayNode();

        ObjectNode criteria =
                JsonNodeFactory.instance.objectNode();

        criteria.put("functionality", 4);
        criteria.put("quality", 4);
        criteria.put("innovation", 4);

        ObjectNode submittedBody =
                JsonNodeFactory.instance.objectNode();

        submittedBody.set("criteria", criteria);
        submittedBody.put("comment", "Looks good.");

        ObjectNode savedBallot =
                JsonNodeFactory.instance.objectNode();

        savedBallot.put("judge", "jdg_01");
        savedBallot.put("project", "prj_07");

        ObjectNode savedCriteria =
                JsonNodeFactory.instance.objectNode();

        savedCriteria.put("functionality", 4);
        savedCriteria.put("quality", 4);
        savedCriteria.put("innovation", 4);

        savedBallot.set("criteria", savedCriteria);
        savedBallot.put("comment", "Looks good.");

        ArrayNode afterSave =
                JsonNodeFactory.instance.arrayNode();

        afterSave.add(savedBallot);

        when(ballotStore.readAll())
                .thenReturn(emptyBallots, afterSave);

        JudgingController controller =
                new JudgingController(
                        authService,
                        rubricStore,
                        ballotStore,
                        assignmentStore,
                        auditStore
                );

        ResponseEntity<?> response =
                controller.saveBallot(
                        "prj_07",
                        submittedBody,
                        request
                );

        assertEquals(200, response.getStatusCode().value());

        JsonNode responseBody =
                (JsonNode) response.getBody();

        assertNotNull(responseBody);
        assertEquals(
                "jdg_01",
                responseBody.path("judge").asText()
        );
        assertEquals(
                "prj_07",
                responseBody.path("project").asText()
        );
        assertEquals(
                4.0,
                responseBody.path("weighted_score").asDouble()
        );

        verify(ballotStore).upsert(
                "jdg_01",
                "prj_07",
                criteria,
                "Looks good."
        );

        ArgumentCaptor<JsonNode> afterCaptor =
                ArgumentCaptor.forClass(JsonNode.class);

        verify(auditStore).append(
                eq("judge_a"),
                eq("BALLOT_SUBMITTED"),
                eq("ballot:jdg_01:prj_07"),
                eq(null),
                afterCaptor.capture(),
                eq("initial_submission"),
                eq("req_test_submit")
        );

        JsonNode auditedBallot =
                afterCaptor.getValue();

        assertEquals(
                "jdg_01",
                auditedBallot.path("judge").asText()
        );
        assertEquals(
                "prj_07",
                auditedBallot.path("project").asText()
        );
    }

    private JsonNode rubric() {
        ObjectNode rubric =
                JsonNodeFactory.instance.objectNode();

        rubric.put("version", 2);

        ArrayNode criteria =
                JsonNodeFactory.instance.arrayNode();

        criteria.add(criterion(
                "functionality",
                "Functionality",
                40,
                5
        ));

        criteria.add(criterion(
                "quality",
                "Quality",
                35,
                5
        ));

        criteria.add(criterion(
                "innovation",
                "Innovation",
                25,
                5
        ));

        rubric.set("criteria", criteria);

        return rubric;
    }

    private ObjectNode criterion(
            String id,
            String name,
            double weight,
            double maxScore
    ) {
        ObjectNode criterion =
                JsonNodeFactory.instance.objectNode();

        criterion.put("id", id);
        criterion.put("name", name);
        criterion.put("weight", weight);
        criterion.put("max_score", maxScore);

        return criterion;
    }
}
