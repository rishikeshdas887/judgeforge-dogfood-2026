package com.dogfood.backend.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Service
public class CommunityVotingService {

    private static final String OPEN_LINK = "OPEN_LINK";
    private static final String EMAIL_GATED = "EMAIL_GATED";
    private static final String AUTHENTICATED = "AUTHENTICATED";

    private static final int VOTE_RATE_LIMIT = 10;
    private static final int COMMENT_RATE_LIMIT = 6;

    private final JsonMapper jsonMapper;
    private final EventStore eventStore;
    private final ProjectStore projectStore;
    private final AuditStore auditStore;
    private final Path votingPath;

    private final Map<String, Deque<Instant>> rateWindows =
            new HashMap<>();

    @Autowired
    public CommunityVotingService(
            JsonMapper jsonMapper,
            EventStore eventStore,
            ProjectStore projectStore,
            AuditStore auditStore
    ) {
        this(
                jsonMapper,
                eventStore,
                projectStore,
                auditStore,
                Path.of("data/community-voting.json")
        );
    }

    CommunityVotingService(
            JsonMapper jsonMapper,
            EventStore eventStore,
            ProjectStore projectStore,
            AuditStore auditStore,
            Path votingPath
    ) {
        this.jsonMapper = jsonMapper;
        this.eventStore = eventStore;
        this.projectStore = projectStore;
        this.auditStore = auditStore;
        this.votingPath = votingPath;

        try {
            Files.createDirectories(votingPath.getParent());

            if (!Files.exists(votingPath)) {
                writeRoot(initialRoot());
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not initialize community voting storage",
                    e
            );
        }
    }

  public synchronized ObjectNode publicConfig() {
    ObjectNode stored =
            (ObjectNode) readRoot()
                    .path("config")
                    .deepCopy();

    ObjectNode config =
            JsonNodeFactory.instance.objectNode();

    config.put(
            "enabled",
            stored.path("enabled").asBoolean(false)
    );

    config.put(
            "access",
            stored.path("access").asText(OPEN_LINK)
    );

    config.put(
            "open",
            stored.path("open").asText("")
    );

    config.put(
            "close",
            stored.path("close").asText("")
    );

    String status =
            votingStatus(stored);

    config.put(
            "status",
            status
    );

    config.put(
            "results_public",
            "CLOSED".equals(status)
    );

    return config;
}

public synchronized ObjectNode organizerConfig() {
    ObjectNode config =
            (ObjectNode) readRoot()
                    .path("config")
                    .deepCopy();

    config.put(
            "status",
            votingStatus(config)
    );

    config.put(
            "results_public",
            "CLOSED".equals(
                    votingStatus(config)
            )
    );

    return config;
}

    public synchronized ObjectNode updateConfig(
            JsonNode input,
            String actorId,
            String requestId
    ) {
        if (input == null || !input.isObject()) {
            throw new IllegalArgumentException(
                    "Voting config must be a JSON object"
            );
        }

        ObjectNode root = readRoot();

        ObjectNode before =
                (ObjectNode) root.deepCopy();

        ObjectNode config =
                (ObjectNode) root.path("config");

        boolean enabled =
                input.path("enabled").asBoolean(
                        config.path("enabled").asBoolean(false)
                );

        String access =
                normalizeAccess(
                        input.path("access").asText(
                                config.path("access")
                                        .asText(OPEN_LINK)
                        )
                );

        String open =
                input.path("open").asText(
                        config.path("open").asText("")
                );

        String close =
                input.path("close").asText(
                        config.path("close").asText("")
                );

        Instant openAt =
                parseInstant(open);

        Instant closeAt =
                parseInstant(close);

        if (!openAt.isBefore(closeAt)) {
            throw new IllegalArgumentException(
                    "Voting open must be before voting close"
            );
        }

        config.put("enabled", enabled);
        config.put("access", access);
        config.put("open", openAt.toString());
        config.put("close", closeAt.toString());

        if (input.path("allowed_emails").isArray()) {
            ArrayNode emails =
                    JsonNodeFactory.instance.arrayNode();

            Set<String> seen =
                    new HashSet<>();

            for (JsonNode node :
                    input.path("allowed_emails")) {

                String email =
                        normalizeEmail(
                                node.asText("")
                        );

                if (!email.isBlank()
                        && seen.add(email)) {
                    emails.add(email);
                }
            }

            config.set(
                    "allowed_emails",
                    emails
            );
        }

        writeRoot(root);

        auditStore.append(
                actorId,
                "COMMUNITY_VOTING_CONFIG_UPDATED",
                "event:" +
                        eventStore.currentEventId(),
                before,
                root,
                "community_voting_config_updated",
                requestId
        );

        return publicConfig();
    }

    public synchronized ObjectNode ballot(
            String voterIdentity
    ) {
        ensureVotingOpen();

        List<JsonNode> projects =
                new ArrayList<>(
                        projectStore.publicSubmitted("", "")
                );

        Collections.shuffle(
                projects,
                new java.util.Random(
                        hash(
                                eventStore.currentEventId()
                                        + "|"
                                        + voterIdentity
                        ).hashCode()
                )
        );

        ObjectNode response =
                JsonNodeFactory.instance.objectNode();

        response.put(
                "event_id",
                eventStore.currentEventId()
        );

        response.put(
                "already_voted",
                findVote(
                        eventStore.currentEventId(),
                        voterIdentity
                ) != null
        );

        ArrayNode ballot =
                JsonNodeFactory.instance.arrayNode();

        for (JsonNode project : projects) {
            ObjectNode item =
                    JsonNodeFactory.instance.objectNode();

            item.put(
                    "id",
                    project.path("id").asText()
            );

            item.put(
                    "title",
                    project.path("title").asText(
                            project.path("name").asText()
                    )
            );

            item.put(
                    "summary",
                    project.path("summary").asText()
            );

            item.put(
                    "track",
                    project.path("track").asText()
            );

            item.put(
                    "team",
                    project.path("team").asText()
            );

            item.put(
                    "repo_url",
                    project.path("repo_url").asText()
            );

            ballot.add(item);
        }

        response.set(
                "projects",
                ballot
        );

        return response;
    }

    public synchronized ObjectNode submitVote(
            String projectId,
            String voterIdentity,
            String clientIp,
            String requestId
    ) {
        ensureVotingOpen();

        enforceRateLimit(
                "vote:ip:" + clientIp,
                VOTE_RATE_LIMIT
        );

        enforceRateLimit(
                "vote:identity:" + voterIdentity,
                VOTE_RATE_LIMIT
        );

        String eventId =
                eventStore.currentEventId();

        if (findVote(
                eventId,
                voterIdentity
        ) != null) {

            auditStore.append(
                    "community-voter",
                    "COMMUNITY_VOTE_DUPLICATE",
                    "event:" + eventId,
                    null,
                    null,
                    "duplicate_community_vote",
                    requestId
            );

            throw new IllegalStateException(
                    "This voter has already voted"
            );
        }

        JsonNode project =
                projectStore.find(projectId);

        if (project == null
                || !"SUBMITTED".equals(
                project.path("status").asText()
        )
                || !eventId.equals(
                project.path("event_id").asText()
        )) {
            throw new IllegalArgumentException(
                    "Project is not available for community voting"
            );
        }

        ObjectNode root = readRoot();

        ObjectNode vote =
                JsonNodeFactory.instance.objectNode();

        vote.put(
                "id",
                "cv_" + UUID.randomUUID()
        );

        vote.put(
                "event_id",
                eventId
        );

        vote.put(
                "voter_hash",
                hash(voterIdentity)
        );

        vote.put(
                "project",
                projectId
        );

        vote.put(
                "created_at",
                Instant.now().toString()
        );

        root.withArray("votes")
                .add(vote);

        writeRoot(root);

        auditStore.append(
                "community-voter",
                "COMMUNITY_VOTE_SUBMITTED",
                "event:" + eventId,
                null,
                voteAudit(projectId),
                "community_vote_submitted",
                requestId
        );

        ObjectNode response =
                JsonNodeFactory.instance.objectNode();

        response.put(
                "status",
                "recorded"
        );

        response.put(
                "project",
                projectId
        );

        return response;
    }

    public synchronized ObjectNode results(
            boolean organizer
    ) {
        ObjectNode config =
                (ObjectNode) readRoot()
                        .path("config")
                        .deepCopy();

        String status =
                votingStatus(config);

        if ("OPEN".equals(status)
                && !organizer) {
            throw new IllegalStateException(
                    "Community voting results are hidden while voting is open"
            );
        }

        if ("UPCOMING".equals(status)
                || "DISABLED".equals(status)) {
            throw new IllegalStateException(
                    "Community voting results are not available"
            );
        }

        String eventId =
                eventStore.currentEventId();

        Map<String, Integer> counts =
                new HashMap<>();

        int totalVotes = 0;

        for (JsonNode vote :
                readRoot().withArray("votes")) {

            if (eventId.equals(
                    vote.path("event_id").asText()
            )) {
                String project =
                        vote.path("project").asText();

                counts.merge(
                        project,
                        1,
                        Integer::sum
                );

                totalVotes++;
            }
        }

        List<ObjectNode> rows =
                new ArrayList<>();

        for (JsonNode project :
                projectStore.publicSubmitted("", "")) {

            String projectId =
                    project.path("id").asText();

            int votes =
                    counts.getOrDefault(
                            projectId,
                            0
                    );

            ObjectNode row =
                    JsonNodeFactory.instance.objectNode();

            row.put(
                    "project",
                    projectId
            );

            row.put(
                    "title",
                    project.path("title").asText(
                            project.path("name").asText()
                    )
            );

            row.put(
                    "track",
                    project.path("track").asText()
            );

            row.put(
                    "votes",
                    votes
            );

            row.put(
                    "percentage",
                    totalVotes == 0
                            ? 0.0
                            : votes * 100.0 / totalVotes
            );

            rows.add(row);
        }

        rows.sort(
                Comparator
                        .comparing(
                                (ObjectNode row) ->
                                        row.path("votes").asInt(),
                                Comparator.reverseOrder()
                        )
                        .thenComparing(
                                row -> row.path("title").asText(),
                                String.CASE_INSENSITIVE_ORDER
                        )
        );

        ArrayNode ranking =
                JsonNodeFactory.instance.arrayNode();

        int rank = 0;
        int previousVotes =
                Integer.MIN_VALUE;

        for (ObjectNode row : rows) {

            int votes =
                    row.path("votes").asInt();

            if (votes != previousVotes) {
                rank++;
                previousVotes = votes;
            }

            row.put(
                    "rank",
                    rank
            );

            ranking.add(row);
        }

        ObjectNode response =
                JsonNodeFactory.instance.objectNode();

        response.put(
                "event_id",
                eventId
        );

        response.put(
                "voting_status",
                status
        );

        response.put(
                "total_votes",
                totalVotes
        );

        response.set(
                "results",
                ranking
        );

        return response;
    }

    public synchronized ArrayNode comments(
            String projectId
    ) {
        ensureGalleryProject(projectId);

        String eventId =
                eventStore.currentEventId();

        List<JsonNode> rows =
                new ArrayList<>();

        for (JsonNode comment :
                readRoot().withArray("comments")) {

            if (eventId.equals(
                    comment.path("event_id").asText()
            )
                    && projectId.equals(
                    comment.path("project").asText()
            )) {
                rows.add(comment);
            }
        }

        rows.sort(
                Comparator.comparing(
                        node ->
                                node.path(
                                        "created_at"
                                ).asText()
                )
        );

        ArrayNode result =
                JsonNodeFactory.instance.arrayNode();

        for (JsonNode comment : rows) {

            ObjectNode publicComment =
                    JsonNodeFactory.instance.objectNode();

            publicComment.put(
                    "id",
                    comment.path("id").asText()
            );

            publicComment.put(
                    "display_name",
                    comment.path(
                            "display_name"
                    ).asText("Anonymous")
            );

            publicComment.put(
                    "body",
                    comment.path("body").asText()
            );

            publicComment.put(
                    "created_at",
                    comment.path(
                            "created_at"
                    ).asText()
            );

            result.add(publicComment);
        }

        return result;
    }

    public synchronized ObjectNode addComment(
            String projectId,
            String voterIdentity,
            String clientIp,
            String displayName,
            String body,
            String requestId
    ) {
        ensureGalleryProject(projectId);

        enforceRateLimit(
                "comment:ip:" + clientIp,
                COMMENT_RATE_LIMIT
        );

        enforceRateLimit(
                "comment:identity:" + voterIdentity,
                COMMENT_RATE_LIMIT
        );

        String cleanName =
                displayName == null
                        ? ""
                        : displayName.trim();

        String cleanBody =
                body == null
                        ? ""
                        : body.trim();

        if (cleanName.length() < 2
                || cleanName.length() > 60) {
            throw new IllegalArgumentException(
                    "Display name must be 2-60 characters"
            );
        }

        if (cleanBody.isBlank()
                || cleanBody.length() > 1000) {
            throw new IllegalArgumentException(
                    "Comment must be 1-1000 characters"
            );
        }

        String eventId =
                eventStore.currentEventId();

        String voterHash =
                hash(voterIdentity);

        String normalizedBody =
                cleanBody.toLowerCase(Locale.ROOT);

        for (JsonNode existing :
                readRoot().withArray("comments")) {

            if (eventId.equals(
                    existing.path(
                            "event_id"
                    ).asText()
            )
                    && projectId.equals(
                    existing.path(
                            "project"
                    ).asText()
            )
                    && voterHash.equals(
                    existing.path(
                            "voter_hash"
                    ).asText()
            )
                    && normalizedBody.equals(
                    existing.path(
                            "body"
                    ).asText()
                            .toLowerCase(Locale.ROOT)
            )) {

                auditStore.append(
                        "community-voter",
                        "COMMUNITY_COMMENT_DUPLICATE",
                        "project:" + projectId,
                        null,
                        null,
                        "duplicate_community_comment",
                        requestId
                );

                throw new IllegalStateException(
                        "Duplicate comment detected"
                );
            }
        }

        ObjectNode root =
                readRoot();

        ObjectNode comment =
                JsonNodeFactory.instance.objectNode();

        comment.put(
                "id",
                "cc_" + UUID.randomUUID()
        );

        comment.put(
                "event_id",
                eventId
        );

        comment.put(
                "project",
                projectId
        );

        comment.put(
                "voter_hash",
                voterHash
        );

        comment.put(
                "display_name",
                cleanName
        );

        comment.put(
                "body",
                cleanBody
        );

        comment.put(
                "created_at",
                Instant.now().toString()
        );

        root.withArray("comments")
                .add(comment);

        writeRoot(root);

        auditStore.append(
                "community-voter",
                "COMMUNITY_COMMENT_CREATED",
                "project:" + projectId,
                null,
                commentAudit(projectId),
                "community_comment_created",
                requestId
        );

        ObjectNode publicComment = JsonNodeFactory.instance.objectNode();

        publicComment.put("id", comment.path("id").asText());
        publicComment.put("event_id", comment.path("event_id").asText());
        publicComment.put("project", comment.path("project").asText());
        publicComment.put("display_name", comment.path("display_name").asText());
        publicComment.put("body", comment.path("body").asText());
        publicComment.put("created_at", comment.path("created_at").asText());

        return publicComment;
    }

    public String normalizeEmail(
            String email
    ) {
        return email == null
                ? ""
                : email.trim()
                        .toLowerCase(Locale.ROOT);
    }

    public boolean isAllowedEmail(
            String email
    ) {
        String normalized =
                normalizeEmail(email);

        for (JsonNode node :
                readRoot()
                        .path("config")
                        .path("allowed_emails")) {

            if (normalized.equals(
                    normalizeEmail(
                            node.asText("")
                    )
            )) {
                return true;
            }
        }

        return false;
    }

    private void ensureVotingOpen() {
        String status =
                votingStatus(
                        readRoot().path("config")
                );

        if (!"OPEN".equals(status)) {
            throw new IllegalStateException(
                    "Community voting is not currently open"
            );
        }
    }

    private void ensureGalleryProject(
            String projectId
    ) {
        JsonNode project =
                projectStore.find(projectId);

        if (project == null
                || !"SUBMITTED".equals(
                project.path("status").asText()
        )
                || !eventStore.currentEventId().equals(
                project.path("event_id").asText()
        )) {

            throw new IllegalArgumentException(
                    "Project is not available in the public gallery"
            );
        }
    }

    private String votingStatus(
            JsonNode config
    ) {
        if (!config.path("enabled")
                .asBoolean(false)) {
            return "DISABLED";
        }

        String open =
                config.path("open").asText("");

        String close =
                config.path("close").asText("");

        if (open.isBlank()
                || close.isBlank()) {
            return "DISABLED";
        }

        try {
            Instant now = Instant.now();
            Instant openAt = Instant.parse(open);
            Instant closeAt = Instant.parse(close);

            if (now.isBefore(openAt)) {
                return "UPCOMING";
            }

            if (!now.isBefore(closeAt)) {
                return "CLOSED";
            }

            return "OPEN";

        } catch (Exception e) {
            return "DISABLED";
        }
    }

    private String normalizeAccess(
            String access
    ) {
        String normalized =
                access == null
                        ? ""
                        : access.trim()
                                .toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case OPEN_LINK -> OPEN_LINK;
            case EMAIL_GATED -> EMAIL_GATED;
            case AUTHENTICATED -> AUTHENTICATED;
            default ->
                    throw new IllegalArgumentException(
                            "access must be OPEN_LINK, EMAIL_GATED, or AUTHENTICATED"
                    );
        };
    }

    private Instant parseInstant(
            String value
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Voting timestamps are required"
            );
        }

        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Voting timestamps must be ISO-8601"
            );
        }
    }

    private ObjectNode initialRoot() {
        ObjectNode root =
                JsonNodeFactory.instance.objectNode();

        root.put(
                "version",
                1
        );

        ObjectNode config =
                JsonNodeFactory.instance.objectNode();

        config.put(
                "enabled",
                true
        );

        config.put(
                "access",
                OPEN_LINK
        );

        config.put(
                "open",
                "2026-09-25T18:00:00Z"
        );

        config.put(
                "close",
                "2026-10-09T18:00:00Z"
        );

        config.set(
                "allowed_emails",
                JsonNodeFactory.instance.arrayNode()
        );

        root.set(
                "config",
                config
        );

        root.set(
                "votes",
                JsonNodeFactory.instance.arrayNode()
        );

        root.set(
                "comments",
                JsonNodeFactory.instance.arrayNode()
        );

        return root;
    }

    private ObjectNode readRoot() {
        try {
            JsonNode root =
                    jsonMapper.readTree(
                            Files.readString(
                                    votingPath
                            )
                    );

            if (!root.isObject()) {
                throw new IllegalStateException(
                        "community-voting.json must contain an object"
                );
            }

            return (ObjectNode) root;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read community voting storage",
                    e
            );
        }
    }

    private void writeRoot(
            ObjectNode root
    ) {
        try {
            Path temp =
                    votingPath.resolveSibling(
                            "community-voting.json.tmp"
                    );

            Files.writeString(
                    temp,
                    jsonMapper.writeValueAsString(
                            root
                    )
            );

            Files.move(
                    temp,
                    votingPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not write community voting storage",
                    e
            );
        }
    }

    private JsonNode findVote(
            String eventId,
            String voterIdentity
    ) {
        String voterHash =
                hash(voterIdentity);

        for (JsonNode vote :
                readRoot().withArray("votes")) {

            if (eventId.equals(
                    vote.path("event_id").asText()
            )
                    && voterHash.equals(
                    vote.path("voter_hash").asText()
            )) {
                return vote;
            }
        }

        return null;
    }

    private void enforceRateLimit(
            String key,
            int limit
    ) {
        Instant now =
                Instant.now();

        Deque<Instant> timestamps =
                rateWindows.computeIfAbsent(
                        key,
                        ignored ->
                                new ArrayDeque<>()
                );

        Instant cutoff =
                now.minus(
                        Duration.ofMinutes(1)
                );

        while (!timestamps.isEmpty()
                && timestamps.peekFirst()
                        .isBefore(cutoff)) {
            timestamps.removeFirst();
        }

        if (timestamps.size() >= limit) {
            auditStore.append(
                    "community-voter",
                    "COMMUNITY_RATE_LIMITED",
                    key,
                    null,
                    null,
                    "rate_limit_exceeded",
                    null
            );

            throw new IllegalStateException(
                    "Too many requests. Please try again in a minute."
            );
        }

        timestamps.addLast(now);
    }

    private ObjectNode voteAudit(
            String projectId
    ) {
        ObjectNode audit =
                JsonNodeFactory.instance.objectNode();

        audit.put(
                "project",
                projectId
        );

        audit.put(
                "event_id",
                eventStore.currentEventId()
        );

        return audit;
    }

    private ObjectNode commentAudit(
            String projectId
    ) {
        return voteAudit(projectId);
    }

    private String hash(
            String value
    ) {
        try {
            var digest =
                    java.security.MessageDigest
                            .getInstance("SHA-256");

            byte[] bytes =
                    digest.digest(
                            value.getBytes(
                                    java.nio.charset.StandardCharsets.UTF_8
                            )
                    );

            StringBuilder result =
                    new StringBuilder();

            for (byte b : bytes) {
                result.append(
                        String.format(
                                "%02x",
                                b
                        )
                );
            }

            return result.toString();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not hash voter identity",
                    e
            );
        }
    }
}