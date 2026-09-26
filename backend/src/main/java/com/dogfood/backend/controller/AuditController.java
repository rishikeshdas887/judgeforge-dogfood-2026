package com.dogfood.backend.controller;

import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.service.AuditStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditController {

    private final AuthService authService;
    private final AuditStore auditStore;

    public AuditController(
            AuthService authService,
            AuditStore auditStore
    ) {
        this.authService = authService;
        this.auditStore = auditStore;
    }

    @GetMapping("/api/audit")
    public ResponseEntity<?> audit(
            HttpServletRequest request
    ) {
        var user =
                authService.currentUser(request);

        if (user.isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Authentication required");
        }

        if (!AuthService.isOrganizerOrAdmin(user.get().role())) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        return ResponseEntity.ok(
                auditStore.readAll()
        );
    }
}
