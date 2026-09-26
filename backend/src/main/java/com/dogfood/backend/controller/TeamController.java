package com.dogfood.backend.controller;

import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.service.TeamStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final AuthService authService;
    private final TeamStore teamStore;

    public TeamController(
            AuthService authService,
            TeamStore teamStore
    ) {
        this.authService = authService;
        this.teamStore = teamStore;
    }

    @GetMapping
    public ResponseEntity<?> teams(
            HttpServletRequest request
    ) {
        if (!isParticipant(request)) {
            return ResponseEntity.status(403)
                    .body("Participant access required");
        }

        return ResponseEntity.ok(teamStore.readTeams());
    }

    @PostMapping
    public ResponseEntity<?> createTeam(
            @RequestBody JsonNode body,
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        if (user.get().role() != AuthService.Role.PARTICIPANT) {
            return ResponseEntity.status(403)
                    .body("Participant access required");
        }

        try {
            return ResponseEntity.status(201).body(
                    teamStore.createTeam(
                            body.path("name").asText(""),
                            user.get().id()
                    )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

    @PostMapping("/{teamId}/invites")
    public ResponseEntity<?> createInvite(
            @PathVariable String teamId,
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        if (user.get().role() != AuthService.Role.PARTICIPANT) {
            return ResponseEntity.status(403)
                    .body("Participant access required");
        }

        try {
            return ResponseEntity.ok(
                    teamStore.createInvite(
                            teamId,
                            user.get().id()
                    )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

    @PostMapping("/invites/{token}/accept")
    public ResponseEntity<?> acceptInvite(
            @PathVariable String token,
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        if (user.get().role() != AuthService.Role.PARTICIPANT) {
            return ResponseEntity.status(403)
                    .body("Participant access required");
        }

        try {
            return ResponseEntity.ok(
                    teamStore.acceptInvite(
                            token,
                            user.get().id()
                    )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

    private boolean isParticipant(
            HttpServletRequest request
    ) {
        var user = authService.currentUser(request);

        return user.isPresent()
                && user.get().role()
                == AuthService.Role.PARTICIPANT;
    }
}
