package com.dogfood.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunityVotingServiceTest {

    @TempDir
    Path tempDir;

    private final JsonMapper jsonMapper = new JsonMapper();

    private EventStore eventStore;
    private ProjectStore projectStore;
    private AuditStore auditStore;
    private CommunityVotingService service;

    @BeforeEach
    void setUp() throws Exception {
        eventStore = mock(EventStore.class);
        projectStore = mock(ProjectStore.class);
        auditStore = mock(AuditStore.class);

        when(eventStore.currentEventId()).thenReturn("evt_01");
        when(projectStore.publicSubmitted(anyString(), anyString()))
                .thenReturn(List.of(
                        project("prj_01"),
                        project("prj_02"),
                        project("prj_03"),
                        project("prj_04")
                ));
        when(projectStore.find(anyString())).thenAnswer(invocation -> {
            String projectId = invocation.getArgument(0, String.class);
            return switch (projectId) {
                case "prj_01", "prj_02", "prj_03", "prj_04" -> project(projectId);
                default -> null;
            };
        });

        Path votingPath = tempDir.resolve("data/community-voting.json");
        Files.createDirectories(votingPath.getParent());
        writeOpenVotingConfig(votingPath);

        service = new CommunityVotingService(
                jsonMapper,
                eventStore,
                projectStore,
                auditStore,
                votingPath
        );
    }

    @Test
    void ballotOrderingIsStableForSameVoter() {
        ObjectNode first = service.ballot("open:voter-a");
        ObjectNode second = service.ballot("open:voter-a");

        assertEquals(first, second);
    }

    @Test
    void publicConfigDoesNotExposeAllowedEmails() throws Exception {
        Path votingPath = tempDir.resolve("data/community-voting.json");
        ObjectNode root = (ObjectNode) jsonMapper.readTree(
                Files.readString(votingPath)
        );
        root.path("config");
        ((ObjectNode) root.path("config"))
                .putArray("allowed_emails")
                .add("judge@example.com");
        Files.writeString(votingPath, jsonMapper.writeValueAsString(root));

        service = new CommunityVotingService(
                jsonMapper,
                eventStore,
                projectStore,
                auditStore,
                votingPath
        );

        ObjectNode publicConfig = service.publicConfig();

        assertFalse(publicConfig.has("allowed_emails"));
        assertTrue(publicConfig.path("enabled").asBoolean());
    }

    @Test
    void duplicateVoteIsRejected() {
        service.submitVote(
                "prj_01",
                "open:voter-a",
                "10.0.0.10",
                "req-1"
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.submitVote(
                        "prj_01",
                        "open:voter-a",
                        "10.0.0.10",
                        "req-2"
                )
        );
    }

    @Test
    void invalidProjectVoteIsRejected() {
        assertThrows(
                RuntimeException.class,
                () -> service.submitVote(
                        "missing-project",
                        "open:voter-a",
                        "10.0.0.10",
                        "req-1"
                )
        );
    }

    @Test
    void publicResultsStayHiddenWhileVotingIsOpen() {
        assertThrows(
                IllegalStateException.class,
                () -> service.results(false)
        );

        assertNotNull(service.results(true));
    }

    @Test
    void commentResponseDoesNotExposeVoterHash() {
        ObjectNode response = service.addComment(
                "prj_01",
                "open:voter-a",
                "10.0.0.10",
                "Rishi",
                "Great project",
                "req-comment-1"
        );

        assertFalse(response.has("voter_hash"));
        assertEquals("Rishi", response.path("display_name").asText());
        assertEquals("Great project", response.path("body").asText());
    }

    @Test
    void duplicateCommentIsRejected() {
        service.addComment(
                "prj_01",
                "open:voter-a",
                "10.0.0.10",
                "Rishi",
                "Great project",
                "req-comment-1"
        );

        assertThrows(
                RuntimeException.class,
                () -> service.addComment(
                        "prj_01",
                        "open:voter-a",
                        "10.0.0.10",
                        "Rishi",
                        "Great project",
                        "req-comment-2"
                )
        );
    }

    @Test
    void voteIpRateLimitBlocksEleventhIdentity() {
        for (int i = 1; i <= 10; i++) {
            service.submitVote(
                    projectIdForVote(i),
                    "open:voter-" + i,
                    "10.0.0.20",
                    "req-" + i
            );
        }

        assertThrows(
                IllegalStateException.class,
                () -> service.submitVote(
                        "prj_01",
                        "open:voter-11",
                        "10.0.0.20",
                        "req-11"
                )
        );
    }

    @Test
    void commentIdentityRateLimitBlocksSeventhCommentAcrossIps() {
        for (int i = 1; i <= 6; i++) {
            service.addComment(
                    "prj_01",
                    "open:voter-commenter",
                    "10.0.0." + (30 + i),
                    "Rishi",
                    "Comment " + i,
                    "comment-" + i
            );
        }

        assertThrows(
                IllegalStateException.class,
                () -> service.addComment(
                        "prj_01",
                        "open:voter-commenter",
                        "10.0.0.99",
                        "Rishi",
                        "Comment 7",
                        "comment-7"
                )
        );
    }

    @Test
    void blankAndOversizedCommentsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.addComment(
                        "prj_01",
                        "open:voter-a",
                        "10.0.0.40",
                        "Rishi",
                        " ",
                        "req-blank"
                )
        );

        String oversized = "x".repeat(1001);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.addComment(
                        "prj_02",
                        "open:voter-b",
                        "10.0.0.41",
                        "Rishi",
                        oversized,
                        "req-large"
                )
        );
    }

    private String projectIdForVote(int i) {
        return switch (i) {
            case 1, 5, 9 -> "prj_01";
            case 2, 6, 10 -> "prj_02";
            case 3, 7 -> "prj_03";
            case 4, 8 -> "prj_04";
            default -> throw new IllegalArgumentException("Unsupported test index");
        };
    }

    private ObjectNode project(String id) {
        ObjectNode project = jsonMapper.createObjectNode();
        project.put("id", id);
        project.put("event_id", "evt_01");
        project.put("status", "SUBMITTED");
        project.put("title", "Project " + id);
        project.put("name", "Project " + id);
        project.put("summary", "Test project");
        project.put("track", "Developer tools");
        project.put("team", "Team " + id);
        project.put("repo_url", "https://example.org/" + id);
        project.put("demo_url", "https://example.org/demo/" + id);
        return project;
    }

    private void writeOpenVotingConfig(Path votingPath) throws IOException {
        Instant now = Instant.now();

        ObjectNode root = jsonMapper.createObjectNode();
        root.put("version", 1);

        ObjectNode config = root.putObject("config");
        config.put("enabled", true);
        config.put("access", "OPEN_LINK");
        config.put(
                "open",
                now.minus(1, ChronoUnit.MINUTES).toString()
        );
        config.put(
                "close",
                now.plus(1, ChronoUnit.HOURS).toString()
        );
        config.putArray("allowed_emails");

        root.putArray("votes");
        root.putArray("comments");

        Files.writeString(
                votingPath,
                jsonMapper.writeValueAsString(root)
        );
    }
}
