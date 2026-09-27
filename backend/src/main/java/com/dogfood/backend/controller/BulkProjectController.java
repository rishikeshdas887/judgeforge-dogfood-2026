package com.dogfood.backend.controller;

import com.dogfood.backend.security.AuthService;
import com.dogfood.backend.security.RequestIdFilter;
import com.dogfood.backend.service.AuditStore;
import com.dogfood.backend.service.BulkProjectService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/organizer/projects")
public class BulkProjectController {

    private final AuthService authService;
    private final BulkProjectService bulkProjectService;
    private final AuditStore auditStore;

    public BulkProjectController(
            AuthService authService,
            BulkProjectService bulkProjectService,
            AuditStore auditStore
    ) {
        this.authService = authService;
        this.bulkProjectService = bulkProjectService;
        this.auditStore = auditStore;
    }

    @GetMapping(
            value = "/export.csv",
            produces = "text/csv"
    )
    public ResponseEntity<?> export(
            HttpServletRequest request
    ) {
        if (!isOrganizer(request)) {
            return ResponseEntity.status(403)
                    .body("Organizer access required");
        }

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"projects.csv\""
                )
                .contentType(
                        MediaType.parseMediaType(
                                "text/csv"
                        )
                )
                .body(
                        bulkProjectService.exportCsv()
                );
    }

    @PostMapping(
            value = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> importProjects(
            @RequestParam("file")
            MultipartFile file,
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

        if (file == null
                || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("CSV file is required");
        }

        try {
            int count =
                    bulkProjectService.importCsv(
                            file.getBytes()
                    );

            var result =
                    new java.util.LinkedHashMap<
                            String,
                            Object
                    >();

            result.put(
                    "imported",
                    count
            );
            result.put(
                    "filename",
                    file.getOriginalFilename()
            );

            auditStore.append(
                    user.get().id(),
                    "PROJECTS_BULK_IMPORTED",
                    "projects:bulk",
                    null,
                    null,
                    "bulk_project_import:" + count,
                    RequestIdFilter.getRequestId(
                            request
                    )
            );

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());

        } catch (Exception e) {
            return ResponseEntity.status(
                            HttpStatus.INTERNAL_SERVER_ERROR
                    )
                    .body(
                            "Could not import project CSV"
                    );
        }
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
