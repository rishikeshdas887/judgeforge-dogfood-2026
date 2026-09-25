package com.dogfood.backend.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class FixtureStore {

    private final JsonNode root;

    public FixtureStore(JsonMapper jsonMapper) {
        try {
            ClassPathResource resource =
                    new ClassPathResource("fixtures.json");

            this.root = jsonMapper.readTree(resource.getInputStream());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not load fixtures.json", e
            );
        }
    }

    public JsonNode event() {
        return root.path("event");
    }

    public List<JsonNode> projects() {
        return toList(root.path("projects"));
    }

    public List<JsonNode> judges() {
        return toList(root.path("judges"));
    }

    public List<JsonNode> scores() {
        return toList(root.path("scores"));
    }

    public List<JsonNode> tracks() {
        return toList(root.path("tracks"));
    }

    public List<JsonNode> teams() {
        return toList(root.path("teams"));
    }

    public boolean submissionsClosed() {
        String closeTime = event()
                .path("submissions_close")
                .asText();

        return Instant.parse(closeTime).isBefore(Instant.now());
    }

    private List<JsonNode> toList(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();

        if (node.isArray()) {
            node.forEach(result::add);
        }

        return result;
    }
}
