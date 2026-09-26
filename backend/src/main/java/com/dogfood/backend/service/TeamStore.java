package com.dogfood.backend.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;

@Service
public class TeamStore {

    private final JsonMapper jsonMapper;
    private final FixtureStore fixtureStore;
    private final Path teamPath;

    public TeamStore(
            JsonMapper jsonMapper,
            FixtureStore fixtureStore
    ) {
        this.jsonMapper = jsonMapper;
        this.fixtureStore = fixtureStore;
        this.teamPath = Path.of("data/teams.json");

        try {
            Files.createDirectories(teamPath.getParent());

            if (!Files.exists(teamPath)) {
                seedFromFixtures();
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not initialize team storage", e
            );
        }
    }

    public synchronized ObjectNode readRoot() {
        try {
            JsonNode root = jsonMapper.readTree(
                    Files.readString(teamPath)
            );

            if (!root.isObject()) {
                throw new IllegalStateException(
                        "teams.json must contain an object"
                );
            }

            return (ObjectNode) root;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read team storage", e
            );
        }
    }

    public synchronized ArrayNode readTeams() {
        JsonNode teams = readRoot().path("teams");

        if (!teams.isArray()) {
            throw new IllegalStateException(
                    "teams.json must contain a teams array"
            );
        }

        return (ArrayNode) teams;
    }

    public synchronized ObjectNode createTeam(
            String name,
            String memberId
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Team name is required"
            );
        }

        if (memberId == null || memberId.isBlank()) {
            throw new IllegalArgumentException(
                    "Team member is required"
            );
        }

        ObjectNode root = readRoot();
        ArrayNode teams = (ArrayNode) root.path("teams");

        String teamId = "tm_" + UUID.randomUUID();

        ObjectNode team =
                JsonNodeFactory.instance.objectNode();

        team.put("id", teamId);
        team.put("name", name.trim());
        team.put("created_by", memberId);
        team.put("created_at", Instant.now().toString());

        ArrayNode members =
                JsonNodeFactory.instance.arrayNode();

        members.add(memberId);
        team.set("members", members);

        teams.add(team);
        root.set("teams", teams);

        write(root);

        return (ObjectNode) team.deepCopy();
    }

    public synchronized boolean isMember(
            String teamId,
            String memberId
    ) {
        JsonNode team = findTeam(teamId);

        if (team == null) {
            return false;
        }

        for (JsonNode member : team.path("members")) {
            if (memberId.equals(member.asText())) {
                return true;
            }
        }

        return false;
    }

    public synchronized ObjectNode createInvite(
            String teamId,
            String inviterId
    ) {
        ObjectNode root = readRoot();
        JsonNode team = findTeam(teamId);

        if (team == null) {
            throw new IllegalArgumentException(
                    "Unknown team: " + teamId
            );
        }

        if (!isMember(teamId, inviterId)) {
            throw new IllegalArgumentException(
                    "Only a team member can create an invite"
            );
        }

        ArrayNode invites =
                root.withArray("invites");

        String token =
                UUID.randomUUID().toString();

        ObjectNode invite =
                JsonNodeFactory.instance.objectNode();

        invite.put("token", token);
        invite.put("team", teamId);
        invite.put("invited_by", inviterId);
        invite.put("status", "PENDING");
        invite.put("created_at", Instant.now().toString());

        invites.add(invite);
        root.set("invites", invites);

        write(root);

        return (ObjectNode) invite.deepCopy();
    }

    public synchronized ObjectNode acceptInvite(
            String token,
            String memberId
    ) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "Invite token is required"
            );
        }

        if (memberId == null || memberId.isBlank()) {
            throw new IllegalArgumentException(
                    "Member is required"
            );
        }

        ObjectNode root = readRoot();
        ArrayNode invites =
                (ArrayNode) root.path("invites");

        if (!invites.isArray()) {
            throw new IllegalStateException(
                    "teams.json must contain an invites array"
            );
        }

        ObjectNode matchedInvite = null;
        String teamId = null;

        for (JsonNode node : invites) {
            if (token.equals(node.path("token").asText())
                    && "PENDING".equals(
                    node.path("status").asText())) {

                matchedInvite = (ObjectNode) node;
                teamId = node.path("team").asText();
                break;
            }
        }

        if (matchedInvite == null) {
            throw new IllegalArgumentException(
                    "Invite is invalid or already used"
            );
        }

        ObjectNode team =
                findTeamObject(root, teamId);

        if (team == null) {
            throw new IllegalStateException(
                    "Invite references an unknown team"
            );
        }

        ArrayNode members =
                team.withArray("members");

        boolean alreadyMember = false;

        for (JsonNode member : members) {
            if (memberId.equals(member.asText())) {
                alreadyMember = true;
                break;
            }
        }

        if (!alreadyMember) {
            members.add(memberId);
        }

        matchedInvite.put("status", "ACCEPTED");
        matchedInvite.put("accepted_by", memberId);
        matchedInvite.put(
                "accepted_at",
                Instant.now().toString()
        );

        write(root);

        return (ObjectNode) team.deepCopy();
    }

    private JsonNode findTeam(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            return null;
        }

        for (JsonNode team : readTeams()) {
            if (teamId.equals(team.path("id").asText())) {
                return team;
            }
        }

        return null;
    }

    private ObjectNode findTeamObject(
            ObjectNode root,
            String teamId
    ) {
        JsonNode teams = root.path("teams");

        if (!teams.isArray()) {
            return null;
        }

        for (JsonNode team : teams) {
            if (teamId.equals(team.path("id").asText())) {
                return (ObjectNode) team;
            }
        }

        return null;
    }

    private void seedFromFixtures() throws IOException {
        ObjectNode root =
                JsonNodeFactory.instance.objectNode();

        root.put("version", 1);

        ArrayNode teams =
                JsonNodeFactory.instance.arrayNode();

        for (JsonNode fixtureTeam : fixtureStore.teams()) {
            teams.add(fixtureTeam.deepCopy());
        }

        root.set("teams", teams);
        root.set(
                "invites",
                JsonNodeFactory.instance.arrayNode()
        );

        write(root);
    }

    private void write(ObjectNode root) {
        try {
            String content =
                    jsonMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(root);

            Path tempPath =
                    teamPath.resolveSibling("teams.json.tmp");

            Files.writeString(tempPath, content);

            try {
                Files.move(
                        tempPath,
                        teamPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (
                    java.nio.file.AtomicMoveNotSupportedException e
            ) {
                Files.move(
                        tempPath,
                        teamPath,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save team storage", e
            );
        }
    }
}
