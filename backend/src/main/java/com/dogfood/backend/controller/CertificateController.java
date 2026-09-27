package com.dogfood.backend.controller;

import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.security.RequestIdFilter;
import com.dogfood.backend.service.CertificateService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class CertificateController {

    private final AuthService authService;
    private final CertificateService certificateService;

    public CertificateController(
            AuthService authService,
            CertificateService certificateService
    ) {
        this.authService = authService;
        this.certificateService = certificateService;
    }

    @PostMapping(
            "/api/organizer/certificates/projects/{projectId}"
    )
    public ResponseEntity<?> issue(
            @PathVariable String projectId,
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
                    certificateService.issueForProject(
                            projectId,
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

    @GetMapping("/api/certificates/{certificateId}")
    public ResponseEntity<?> verify(
            @PathVariable String certificateId
    ) {
        var certificate =
                certificateService.get(
                        certificateId
                );

        if (certificate == null) {
            return ResponseEntity.notFound()
                    .build();
        }

        return ResponseEntity.ok(
                certificate
        );
    }

    @GetMapping("/api/participant/participation-records")
    public ResponseEntity<?> mine(
            HttpServletRequest request
    ) {
        var user =
                authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        if (user.get().role()
                != AuthService.Role.PARTICIPANT) {
            return ResponseEntity.status(403)
                    .body("Participant access required");
        }

        return ResponseEntity.ok(
                certificateService.forParticipant(
                        user.get().id()
                )
        );
    }

    @GetMapping(
            "/api/organizer/certificates/projects/{projectId}"
    )
    public ResponseEntity<?> projectCertificates(
            @PathVariable String projectId,
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

        return ResponseEntity.ok(
                certificateService.forProject(
                        projectId
                )
        );
    }
}
