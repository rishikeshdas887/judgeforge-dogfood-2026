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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectStore {

    private final JsonMapper jsonMapper;
    private final FixtureStore fixtureStore;
    private final EventStore eventStore;
    private final TeamStore teamStore;
    private final Path projectPath;

    public ProjectStore(
            JsonMapper jsonMapper,
            FixtureStore fixtureStore,
            EventStore eventStore,
            TeamStore teamStore
    ) {
        this.jsonMapper = jsonMapper;
        this.fixtureStore = fixtureStore;
        this.eventStore = eventStore;
        this.teamStore = teamStore;
        this.projectPath = Path.of("/app/data/projects.json");

        try {
            Files.createDirectories(projectPath.getParent());

            if (!Files.exists(projectPath)) {
                seedFromFixtures();
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not initialize project storage",
                    e
            );
        }
    }

    public synchronized ArrayNode readAll() {
        try {
            JsonNode root =
                    jsonMapper.readTree(
                            Files.readString(projectPath)
                    );

            if (!root.isObject()) {
                throw new IllegalStateException(
                        "projects.json must contain an object"
                );
            }

            JsonNode projects = root.path("projects");

            if (!projects.isArray()) {
                throw new IllegalStateException(
                        "projects.json must contain a projects array"
                );
            }

            return (ArrayNode) projects;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read project storage",
                    e
            );
        }
    }

    public synchronized List<JsonNode> publicSubmitted(
            String search,
            String track
    ) {
        String normalizedSearch =
                search == null
                        ? ""
                        : search.trim().toLowerCase();

        String normalizedTrack =
                track == null
                        ? ""
                        : track.trim();

        List<JsonNode> results =
                new ArrayList<>();

        String currentEventId =
                eventStore.currentEventId();

        for (JsonNode project : readAll()) {
            if (!"SUBMITTED".equals(
                    project.path("status").asText()
            )) {
                continue;
            }

            if (!currentEventId.equals(
                    project.path("event_id").asText()
            )) {
                continue;
            }

            if (!normalizedTrack.isBlank()
                    && !normalizedTrack.equals(
                    project.path("track").asText()
            )) {
                continue;
            }

            if (!normalizedSearch.isBlank()) {
                String haystack =
                        String.join(
                                " ",
                                project.path("title").asText(),
                                project.path("name").asText(),
                                project.path("tagline").asText(),
                                project.path("summary").asText(),
                                project.path("long_description").asText(),
                                project.path("team").asText(),
                                project.path("track").asText()
                        ).toLowerCase();

                if (!haystack.contains(
                        normalizedSearch
                )) {
                    continue;
                }
            }

            results.add(project.deepCopy());
        }

        return results;
    }

    public synchronized List<JsonNode> projectsForUser(
            String userId
    ) {
        List<JsonNode> results =
                new ArrayList<>();

        for (JsonNode project : readAll()) {
            String teamId =
                    project.path("team").asText("");

            if (teamStore.isMember(
                    teamId,
                    userId
            )) {
                results.add(project.deepCopy());
            }
        }

        return results;
    }

    public synchronized ObjectNode createDraft(
            JsonNode input,
            String userId
    ) {
        ensureSubmissionsOpen();

        validateInput(input);

        String teamId =
                requiredText(input, "team");

        String trackId =
                requiredText(input, "track");

        ensureTeamMembership(
                teamId,
                userId
        );

        ensureTrackExists(trackId);

        ObjectNode project =
                buildProject(
                        input,
                        userId,
                        teamId,
                        trackId,
                        "DRAFT"
                );

        ObjectNode root =
                readRoot();

        root.withArray("projects")
                .add(project);

        writeRoot(root);

        return (ObjectNode) project.deepCopy();
    }

    public synchronized ObjectNode updateDraft(
            String projectId,
            JsonNode input,
            String userId
    ) {
        ensureSubmissionsOpen();

        ObjectNode root =
                readRoot();

        ObjectNode project =
                findProjectObject(
                        root,
                        projectId
                );

        if (project == null) {
            throw new IllegalArgumentException(
                    "Unknown project: " + projectId
            );
        }

        if (!"DRAFT".equals(
                project.path("status").asText()
        )) {
            throw new IllegalArgumentException(
                    "Only draft projects can be edited"
            );
        }

        ensureTeamMembership(
                project.path("team").asText(""),
                userId
        );

        if (input == null || !input.isObject()) {
            throw new IllegalArgumentException(
                    "Project must be a JSON object"
            );
        }

        String inputTeam =
                input.path("team").asText("");

        if (!inputTeam.isBlank()
                && !inputTeam.equals(
                project.path("team").asText()
        )) {
            throw new IllegalArgumentException(
                    "Project team cannot change"
            );
        }

        String trackId =
                input.path("track")
                        .asText(
                                project.path("track")
                                        .asText("")
                        );

        ensureTrackExists(trackId);

        applyEditableFields(
                project,
                input
        );

        project.put(
                "track",
                trackId
        );

        project.put(
                "updated_at",
                Instant.now().toString()
        );

        writeRoot(root);

        return (ObjectNode) project.deepCopy();
    }

    public synchronized ObjectNode submit(
            String projectId,
            String userId
    ) {
        ensureSubmissionsOpen();

        ObjectNode root =
                readRoot();

        ObjectNode project =
                findProjectObject(
                        root,
                        projectId
                );

        if (project == null) {
            throw new IllegalArgumentException(
                    "Unknown project: " + projectId
            );
        }

        ensureTeamMembership(
                project.path("team").asText(""),
                userId
        );

        if (!"DRAFT".equals(
                project.path("status").asText()
        )) {
            throw new IllegalArgumentException(
                    "Only draft projects can be submitted"
            );
        }

        validateCompleteSubmission(project);

        Instant submittedAt =
                Instant.now();

        project.put(
                "status",
                "SUBMITTED"
        );
        project.put(
                "submitted_at",
                submittedAt.toString()
        );
        project.put(
                "updated_at",
                submittedAt.toString()
        );

        writeRoot(root);

        return (ObjectNode) project.deepCopy();
    }

    public synchronized ObjectNode find(
            String projectId
    ) {
        ObjectNode root =
                readRoot();

        ObjectNode project =
                findProjectObject(
                        root,
                        projectId
                );

        return project == null
                ? null
                : (ObjectNode) project.deepCopy();
    }

    private ObjectNode buildProject(
            JsonNode input,
            String userId,
            String teamId,
            String trackId,
            String status
    ) {
        ObjectNode project =
                JsonNodeFactory.instance.objectNode();

        String now =
                Instant.now().toString();

        project.put(
                "id",
                "prj_" + UUID.randomUUID()
        );
        project.put(
                "event_id",
                eventStore.currentEventId()
        );
        project.put(
                "team",
                teamId
        );
        project.put(
                "track",
                trackId
        );

        String name =
                firstNonBlank(
                        input.path("name").asText(""),
                        input.path("title").asText("")
                );

        String longDescription =
                firstNonBlank(
                        input.path("long_description")
                                .asText(""),
                        input.path("summary")
                                .asText("")
                );

        String repositoryUrl =
                firstNonBlank(
                        input.path("repository_url")
                                .asText(""),
                        input.path("repo_url")
                                .asText("")
                );

        project.put(
                "name",
                name
        );
        project.put(
                "title",
                name
        );
        project.put(
                "tagline",
                input.path("tagline").asText("")
        );
        project.put(
                "long_description",
                longDescription
        );
        project.put(
                "summary",
                longDescription
        );
        project.put(
                "thumbnail",
                input.path("thumbnail").asText("")
        );

        JsonNode imageGallery =
                input.path("image_gallery");

        if (imageGallery.isArray()) {
            project.set(
                    "image_gallery",
                    imageGallery.deepCopy()
            );
        } else {
            project.set(
                    "image_gallery",
                    JsonNodeFactory.instance.arrayNode()
            );
        }

        project.put(
                "demo_video_url",
                input.path("demo_video_url")
                        .asText("")
        );
        project.put(
                "repository_url",
                repositoryUrl
        );
        project.put(
                "repo_url",
                repositoryUrl
        );
        project.put(
                "live_link",
                input.path("live_link").asText("")
        );

        JsonNode techTags =
                input.path("tech_tags");

        if (techTags.isArray()) {
            project.set(
                    "tech_tags",
                    techTags.deepCopy()
            );
        } else {
            project.set(
                    "tech_tags",
                    JsonNodeFactory.instance.arrayNode()
            );
        }

        JsonNode customAnswers =
                input.path("custom_answers");

        if (customAnswers.isObject()) {
            project.set(
                    "custom_answers",
                    customAnswers.deepCopy()
            );
        } else {
            project.set(
                    "custom_answers",
                    JsonNodeFactory.instance.objectNode()
            );
        }

        project.put(
                "status",
                status
        );
        project.put(
                "created_by",
                userId
        );
        project.put(
                "created_at",
                now
        );
        project.put(
                "updated_at",
                now
        );

        if ("SUBMITTED".equals(status)) {
            project.put(
                    "submitted_at",
                    now
            );
        }

        return project;
    }

    private void applyEditableFields(
            ObjectNode project,
            JsonNode input
    ) {
        String name =
                firstNonBlank(
                        input.path("name").asText(""),
                        input.path("title").asText("")
                );

        if (!name.isBlank()) {
            project.put("name", name);
            project.put("title", name);
        }

        if (input.has("tagline")) {
            project.put(
                    "tagline",
                    input.path("tagline").asText("")
            );
        }

        if (input.has("long_description")
                || input.has("summary")) {

            String description =
                    firstNonBlank(
                            input.path("long_description")
                                    .asText(""),
                            input.path("summary")
                                    .asText("")
                    );

            project.put(
                    "long_description",
                    description
            );
            project.put(
                    "summary",
                    description
            );
        }

        if (input.has("thumbnail")) {
            project.put(
                    "thumbnail",
                    input.path("thumbnail")
                            .asText("")
            );
        }

        if (input.path("image_gallery")
                .isArray()) {
            project.set(
                    "image_gallery",
                    input.path("image_gallery")
                            .deepCopy()
            );
        }

        if (input.has("demo_video_url")) {
            project.put(
                    "demo_video_url",
                    input.path("demo_video_url")
                            .asText("")
            );
        }

        if (input.has("repository_url")
                || input.has("repo_url")) {

            String repositoryUrl =
                    firstNonBlank(
                            input.path("repository_url")
                                    .asText(""),
                            input.path("repo_url")
                                    .asText("")
                    );

            project.put(
                    "repository_url",
                    repositoryUrl
            );
            project.put(
                    "repo_url",
                    repositoryUrl
            );
        }

        if (input.has("live_link")) {
            project.put(
                    "live_link",
                    input.path("live_link")
                            .asText("")
            );
        }

        if (input.path("tech_tags")
                .isArray()) {
            project.set(
                    "tech_tags",
                    input.path("tech_tags")
                            .deepCopy()
            );
        }

        if (input.path("custom_answers")
                .isObject()) {
            project.set(
                    "custom_answers",
                    input.path("custom_answers")
                            .deepCopy()
            );
        }
    }

    private void validateInput(
            JsonNode input
    ) {
        if (input == null || !input.isObject()) {
            throw new IllegalArgumentException(
                    "Project must be a JSON object"
            );
        }

        String team =
                input.path("team")
                        .asText("");

        String track =
                input.path("track")
                        .asText("");

        String name =
                firstNonBlank(
                        input.path("name").asText(""),
                        input.path("title").asText("")
                );

        if (team.isBlank()) {
            throw new IllegalArgumentException(
                    "team is required"
            );
        }

        if (track.isBlank()) {
            throw new IllegalArgumentException(
                    "track is required"
            );
        }

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "name is required"
            );
        }
    }

    private void validateCompleteSubmission(
            JsonNode project
    ) {
        String name =
                firstNonBlank(
                        project.path("name").asText(""),
                        project.path("title").asText("")
                );

        String description =
                firstNonBlank(
                        project.path("long_description")
                                .asText(""),
                        project.path("summary")
                                .asText("")
                );

        String repositoryUrl =
                firstNonBlank(
                        project.path("repository_url")
                                .asText(""),
                        project.path("repo_url")
                                .asText("")
                );

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "Project name is required before submission"
            );
        }

        if (description.isBlank()) {
            throw new IllegalArgumentException(
                    "Long description is required before submission"
            );
        }

        if (repositoryUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Repository URL is required before submission"
            );
        }

        if (project.path("track")
                .asText("")
                .isBlank()) {
            throw new IllegalArgumentException(
                    "Track is required before submission"
            );
        }

        if (project.path("team")
                .asText("")
                .isBlank()) {
            throw new IllegalArgumentException(
                    "Team is required before submission"
            );
        }
    }

    private void ensureSubmissionsOpen() {
        if (!eventStore.submissionsOpen()) {
            throw new IllegalArgumentException(
                    "Submissions are closed"
            );
        }
    }

    private void ensureTeamMembership(
            String teamId,
            String userId
    ) {
        if (!teamStore.isMember(
                teamId,
                userId
        )) {
            throw new IllegalArgumentException(
                    "You must be a member of the team"
            );
        }
    }

    private void ensureTrackExists(
            String trackId
    ) {
        for (JsonNode track :
                eventStore.read().path("tracks")) {

            if (trackId.equals(
                    track.path("id").asText()
            )) {
                return;
            }
        }

        throw new IllegalArgumentException(
                "Unknown event track: " + trackId
        );
    }

    private ObjectNode readRoot() {
        try {
            JsonNode root =
                    jsonMapper.readTree(
                            Files.readString(projectPath)
                    );

            if (!root.isObject()) {
                throw new IllegalStateException(
                        "projects.json must contain an object"
                );
            }

            return (ObjectNode) root;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read project storage",
                    e
            );
        }
    }

    private ObjectNode findProjectObject(
            ObjectNode root,
            String projectId
    ) {
        JsonNode projects =
                root.path("projects");

        if (!projects.isArray()) {
            return null;
        }

        for (JsonNode project : projects) {
            if (projectId.equals(
                    project.path("id").asText()
            )) {
                return (ObjectNode) project;
            }
        }

        return null;
    }

    private void seedFromFixtures()
            throws IOException {

        ObjectNode root =
                JsonNodeFactory.instance.objectNode();

        root.put(
                "version",
                1
        );

        ArrayNode projects =
                JsonNodeFactory.instance.arrayNode();

        String eventId =
                eventStore.currentEventId();

        for (JsonNode fixtureProject :
                fixtureStore.projects()) {

            ObjectNode project =
                    (ObjectNode)
                            fixtureProject.deepCopy();

            project.put(
                    "event_id",
                    eventId
            );
            project.put(
                    "status",
                    "SUBMITTED"
            );

            project.put(
                    "name",
                    firstNonBlank(
                            project.path("title")
                                    .asText(""),
                            "Untitled project"
                    )
            );

            project.put(
                    "long_description",
                    project.path("summary")
                            .asText("")
            );

            project.put(
                    "repository_url",
                    project.path("repo_url")
                            .asText("")
            );

            project.set(
                    "image_gallery",
                    JsonNodeFactory.instance.arrayNode()
            );

            project.set(
                    "tech_tags",
                    JsonNodeFactory.instance.arrayNode()
            );

            project.set(
                    "custom_answers",
                    JsonNodeFactory.instance.objectNode()
            );

            project.put(
                    "created_by",
                    "fixture"
            );

            project.put(
                    "created_at",
                    project.path("submitted_at")
                            .asText(
                                    Instant.now().toString()
                            )
            );

            project.put(
                    "updated_at",
                    project.path("submitted_at")
                            .asText(
                                    Instant.now().toString()
                            )
            );

            projects.add(project);
        }

        root.set(
                "projects",
                projects
        );

        writeRoot(root);
    }

    private String requiredText(
            JsonNode input,
            String field
    ) {
        String value =
                input.path(field)
                        .asText("");

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " is required"
            );
        }

        return value.trim();
    }

    private String firstNonBlank(
            String first,
            String second
    ) {
        if (first != null
                && !first.isBlank()) {
            return first.trim();
        }

        return second == null
                ? ""
                : second.trim();
    }

    private void writeRoot(
            ObjectNode root
    ) {
        try {
            String content =
                    jsonMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(root);

            Path tempPath =
                    projectPath.resolveSibling(
                            "projects.json.tmp"
                    );

            Files.writeString(
                    tempPath,
                    content
            );

            try {
                Files.move(
                        tempPath,
                        projectPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (
                    java.nio.file.AtomicMoveNotSupportedException e
            ) {
                Files.move(
                        tempPath,
                        projectPath,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save project storage",
                    e
            );
        }
    }
}
