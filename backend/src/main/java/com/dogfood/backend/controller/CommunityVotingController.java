package com.dogfood.backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.security.RequestIdFilter;
import com.dogfood.backend.service.CommunityVotingService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.JsonNode;

@RestController
public class CommunityVotingController {

    private static final String VOTER_COOKIE =
            "community_voter";

    private final AuthService authService;
    private final CommunityVotingService votingService;

    public CommunityVotingController(
            AuthService authService,
            CommunityVotingService votingService
    ) {
        this.authService = authService;
        this.votingService = votingService;
    }

    @GetMapping("/api/community/voting/config")
    public ResponseEntity<?> publicConfig() {
        return ResponseEntity.ok(
                votingService.publicConfig()
        );
    }

    @GetMapping("/api/organizer/community-voting")
    public ResponseEntity<?> organizerConfig(
            HttpServletRequest request
    ) {
        if (!isOrganizer(request)) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        return ResponseEntity.ok(
                votingService.organizerConfig()
        );
    }

    @PutMapping("/api/organizer/community-voting")
    public ResponseEntity<?> updateConfig(
            @RequestBody JsonNode body,
            HttpServletRequest request
    ) {
        var user =
                authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        if (!AuthService.isOrganizerOrAdmin(
                user.get().role()
        )) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        try {
            return ResponseEntity.ok(
                    votingService.updateConfig(
                            body,
                            user.get().id(),
                            RequestIdFilter.getRequestId(
                                    request
                            )
                    )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

    @GetMapping("/api/community/voting/ballot")
    public ResponseEntity<?> ballot(
            @RequestParam(required = false)
            String email,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            String identity =
                    resolveVoterIdentity(
                            request,
                            response,
                            email
                    );

            return ResponseEntity.ok(
                    votingService.ballot(identity)
            );

        } catch (SecurityException e) {
            return ResponseEntity.status(403)
                    .body(e.getMessage());

        } catch (IllegalStateException e) {
            return ResponseEntity.status(409)
                    .body(e.getMessage());
        }
    }

    @PostMapping("/api/community/voting/vote")
    public ResponseEntity<?> vote(
            @RequestBody JsonNode body,
            @RequestParam(required = false)
            String email,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (body == null || !body.isObject()) {
            return ResponseEntity.badRequest()
                    .body("Vote body must be a JSON object");
        }

        String projectId =
                body.path("project_id")
                        .asText("")
                        .trim();

        if (projectId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("project_id is required");
        }

        if (email == null || email.isBlank()) {
            email =
                    body.path("email").asText("");
        }

        try {
            String identity =
                    resolveVoterIdentity(
                            request,
                            response,
                            email
                    );

          String clientIp = resolveClientIp(request);

    return ResponseEntity.status(201)
        .body(
                votingService.submitVote(
                        projectId,
                        identity,
                        clientIp,
                        RequestIdFilter.getRequestId(
                                request
                        )
                )
        );

        } catch (SecurityException e) {
            return ResponseEntity.status(403)
                    .body(e.getMessage());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());

        } catch (IllegalStateException e) {
            String message =
                    e.getMessage() == null
                            ? ""
                            : e.getMessage();

            if (message.startsWith(
                    "Too many requests"
            )) {
                return ResponseEntity.status(429)
                        .body(message);
            }

            return ResponseEntity.status(409)
                    .body(message);
        }
    }

    @GetMapping("/api/community/voting/results")
    public ResponseEntity<?> results(
            HttpServletRequest request
    ) {
        try {
            return ResponseEntity.ok(
                    votingService.results(
                            isOrganizer(request)
                    )
            );
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403)
                    .body(e.getMessage());
        }
    }

    @GetMapping("/api/community/comments")
    public ResponseEntity<?> comments(
            @RequestParam String project
    ) {
        try {
            return ResponseEntity.ok(
                    votingService.comments(project)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

    @PostMapping("/api/community/comments")
    public ResponseEntity<?> addComment(
            @RequestBody JsonNode body,
            @RequestParam(required = false) String email,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (body == null || !body.isObject()) {
            return ResponseEntity.badRequest()
                    .body("Comment body must be a JSON object");
        }

        String projectId =
                body.path("project_id")
                        .asText("")
                        .trim();

        String displayName =
                body.path("display_name")
                        .asText("");

        String comment =
                body.path("body")
                        .asText("");

        if (email == null || email.isBlank()) {
            email = body.path("email").asText("");
        }

        if (projectId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("project_id is required");
        }

        try {
            String identity =
                    resolveVoterIdentity(
                            request,
                            response,
                            email
                    );
            String clientIp =
                   resolveClientIp(request);


                   return ResponseEntity.status(201)
        .body(
                votingService.addComment(
                        projectId,
                        identity,
                        clientIp,
                        displayName,
                        comment,
                        RequestIdFilter.getRequestId(
                                request
                        )
                )
        );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());

        } catch (IllegalStateException e) {
            String message =
                    e.getMessage() == null
                            ? ""
                            : e.getMessage();

            if (message.startsWith(
                    "Too many requests"
            )) {
                return ResponseEntity.status(429)
                        .body(message);
            }

            return ResponseEntity.status(409)
                    .body(message);
        }
    }

    private String resolveVoterIdentity(
            HttpServletRequest request,
            HttpServletResponse response,
            String email
    ) {
        String access =
                votingService.publicConfig()
                        .path("access")
                        .asText();

        return switch (access) {

            case "OPEN_LINK" ->
                    "open:" +
                            resolveAnonymousIdentity(
                                    request,
                                    response
                            );

            case "AUTHENTICATED" -> {
                var user =
                        authService.currentUser(request);

                if (user.isEmpty()) {
                    throw new SecurityException(
                            "Authentication required to vote"
                    );
                }

                yield "user:" + user.get().id();
            }

            case "EMAIL_GATED" -> {
                String normalized =
                        votingService.normalizeEmail(
                                email
                        );

                if (normalized.isBlank()) {
                    throw new SecurityException(
                            "Voting email is required"
                    );
                }

                if (!votingService.isAllowedEmail(
                        normalized
                )) {
                    throw new SecurityException(
                            "This email is not allowed to vote"
                    );
                }

                yield "email:" + normalized;
            }

            default ->
                    throw new SecurityException(
                            "Community voting access is not configured"
                    );
        };
    }

    private String resolveAnonymousIdentity(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (request.getCookies() != null) {
            for (Cookie cookie :
                    request.getCookies()) {

                if (VOTER_COOKIE.equals(
                        cookie.getName()
                )
                        && cookie.getValue() != null
                        && !cookie.getValue().isBlank()) {

                    return cookie.getValue();
                }
            }
        }

        String token =
                UUID.randomUUID().toString();

        Cookie cookie =
                new Cookie(
                        VOTER_COOKIE,
                        token
                );

        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(
                60 * 60 * 24 * 30
        );

        response.addCookie(cookie);

        return token;
    }
private String resolveClientIp(
        HttpServletRequest request
) {
    String forwarded =
            request.getHeader("X-Real-IP");

    if (forwarded != null && !forwarded.isBlank()) {
        return forwarded.trim();
    }

    return request.getRemoteAddr();
}
    private boolean isOrganizer(
            HttpServletRequest request
    ) {
        var user =
                authService.currentUser(request);

        return user.isPresent()
                && AuthService.isOrganizerOrAdmin(
                        user.get().role()
                );
    }
}