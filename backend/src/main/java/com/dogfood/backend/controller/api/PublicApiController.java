package com.dogfood.backend.controller.api;

import com.dogfood.backend.service.EventStore;
import com.dogfood.backend.service.ProjectStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public class PublicApiController {

    private final EventStore eventStore;
    private final ProjectStore projectStore;

    public PublicApiController(
            EventStore eventStore,
            ProjectStore projectStore
    ) {
        this.eventStore = eventStore;
        this.projectStore = projectStore;
    }

    @GetMapping("/event")
    public ResponseEntity<JsonNode> event() {
        return ResponseEntity.ok(eventStore.read());
    }

    @GetMapping("/projects")
    public ResponseEntity<?> projects(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String track
    ) {
        return ResponseEntity.ok(
                projectStore.publicSubmitted(search, track)
        );
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<?> project(
            @PathVariable String projectId
    ) {
        JsonNode project = projectStore.find(projectId);

        if (project == null) {
            return ResponseEntity.notFound().build();
        }

        if (!project.path("status")
                .asText("")
                .equalsIgnoreCase("SUBMITTED")) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(project);
    }
}
