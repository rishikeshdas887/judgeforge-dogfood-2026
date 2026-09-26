package com.dogfood.backend.controller;

import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.service.RubricStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Set;

@RestController
public class JudgingConfigController {

    private final AuthService authService;
    private final RubricStore rubricStore;

    public JudgingConfigController(
            AuthService authService,
            RubricStore rubricStore
    ) {
        this.authService = authService;
        this.rubricStore = rubricStore;
    }

    @GetMapping("/api/organizer/rubric")
    public ResponseEntity<?> getRubric(
            HttpServletRequest request
    ) {
        if (!isOrganizer(request)) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        return ResponseEntity.ok(rubricStore.read());
    }

    @PutMapping("/api/organizer/rubric")
    public ResponseEntity<?> updateRubric(
            @RequestBody JsonNode rubric,
            HttpServletRequest request
    ) {
        if (!isOrganizer(request)) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        String validationError = validateRubric(rubric);

        if (validationError != null) {
            return ResponseEntity.badRequest()
                    .body(validationError);
        }

        rubricStore.write(rubric);

        return ResponseEntity.ok(rubricStore.read());
    }

    private boolean isOrganizer(HttpServletRequest request) {
        var user = authService.currentUser(request);

        return user.isPresent()
                && AuthService.isOrganizerOrAdmin(user.get().role());
    }

    private String validateRubric(JsonNode rubric) {
        if (rubric == null || !rubric.isObject()) {
            return "Rubric must be a JSON object";
        }

        JsonNode version = rubric.path("version");

        if (!version.isIntegralNumber()
                || version.asInt() < 1) {
            return "version must be an integer >= 1";
        }

        JsonNode criteria = rubric.path("criteria");

        if (!criteria.isArray() || criteria.isEmpty()) {
            return "criteria must be a non-empty array";
        }

        Set<String> ids = new HashSet<>();
        double totalWeight = 0.0;

        for (JsonNode criterion : criteria) {
            if (!criterion.isObject()) {
                return "Every criterion must be an object";
            }

            String id = criterion.path("id").asText("");
            String name = criterion.path("name").asText("");

            if (id.isBlank() || name.isBlank()) {
                return "Every criterion requires id and name";
            }

            if (!ids.add(id)) {
                return "Duplicate criterion id: " + id;
            }

            JsonNode weight = criterion.path("weight");
            JsonNode maxScore = criterion.path("max_score");

            if (!weight.isNumber()
                    || weight.asDouble() <= 0) {
                return "Criterion weight must be > 0: " + id;
            }

            if (!maxScore.isNumber()
                    || maxScore.asDouble() <= 0) {
                return "Criterion max_score must be > 0: " + id;
            }

            totalWeight += weight.asDouble();
        }

        if (Math.abs(totalWeight - 100.0) > 0.01) {
            return String.format(
                    "Criterion weights must total 100. Current total: %.2f",
                    totalWeight
            );
        }

        return null;
    }
}
