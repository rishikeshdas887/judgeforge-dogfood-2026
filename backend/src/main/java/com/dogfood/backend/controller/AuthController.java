package com.dogfood.backend.controller;

import com.dogfood.backend.security.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestParam String role,
            HttpServletResponse response
    ) {
        String value = switch (role.toLowerCase()) {
            case "admin" -> "adm_1a2b";
            case "organizer" -> "org_7f2a";
            case "judge_a" -> "jdg_a_91bc";
            case "judge_b" -> "jdg_b_44de";
            case "participant" -> "prt_2e88";
            case "participant_b" -> "prt_b_5c3d";
            default -> null;
        };

        if (value == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Unknown role"));
        }

        Cookie cookie = new Cookie("session", value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        response.addCookie(cookie);

        return ResponseEntity.ok(
                Map.of("message", "logged in", "role", role)
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("session", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ResponseEntity.ok(
                Map.of("message", "logged out")
        );
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        return authService.currentUser(request)
                .map(user -> ResponseEntity.ok(
                        Map.of(
                                "id", user.id(),
                                "role", user.role().name()
                        )
                ))
                .orElseGet(() ->
                        ResponseEntity.status(401)
                                .body(Map.of("message", "Not authenticated"))
                );
    }
}
