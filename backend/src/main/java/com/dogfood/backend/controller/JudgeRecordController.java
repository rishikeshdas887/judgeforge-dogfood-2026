package com.dogfood.backend.controller;

import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.service.JudgeRecordService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class JudgeRecordController {

    private final AuthService authService;
    private final JudgeRecordService judgeRecordService;

    public JudgeRecordController(
            AuthService authService,
            JudgeRecordService judgeRecordService
    ) {
        this.authService = authService;
        this.judgeRecordService = judgeRecordService;
    }

    @PostMapping("/api/organizer/judge-records/snapshot")
    public ResponseEntity<?> snapshot(
            HttpServletRequest request
    ) {
        if (!isOrganizer(request)) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        return ResponseEntity.ok(
                judgeRecordService.snapshot()
        );
    }

    @GetMapping("/api/organizer/judge-records")
    public ResponseEntity<?> all(
            HttpServletRequest request
    ) {
        if (!isOrganizer(request)) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        return ResponseEntity.ok(
                judgeRecordService.all()
        );
    }

    @GetMapping("/api/v1/judge-records/{recordId}")
    public ResponseEntity<?> get(
            @PathVariable String recordId
    ) {
        var record =
                judgeRecordService.get(recordId);

        if (record == null) {
            return ResponseEntity.notFound()
                    .build();
        }

        return ResponseEntity.ok(record);
    }

    @GetMapping(
            "/api/v1/judge-records/{recordId}/verify"
    )
    public ResponseEntity<?> verify(
            @PathVariable String recordId
    ) {
        var result =
                judgeRecordService.verify(recordId);

        if (result == null) {
            return ResponseEntity.notFound()
                    .build();
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/v1/judge-records/public-key")
    public ResponseEntity<?> publicKey() {
        return ResponseEntity.ok(
                judgeRecordService.publicKey()
        );
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
