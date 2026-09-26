package com.dogfood.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    public enum Role {
        ADMIN,
        ORGANIZER,
        JUDGE_A,
        JUDGE_B,
        PARTICIPANT
    }

    public record User(String id, Role role) {}

    public static boolean isOrganizerOrAdmin(Role role) {
        return role == Role.ORGANIZER || role == Role.ADMIN;
    }

    public Optional<User> currentUser(HttpServletRequest request) {
        String cookie = request.getHeader("Cookie");

        if (cookie == null || cookie.isBlank()) {
            return Optional.empty();
        }

        if (cookie.contains("session=adm_1a2b")) {
            return Optional.of(
                    new User("admin", Role.ADMIN)
            );
        }

        if (cookie.contains("session=org_7f2a")) {
            return Optional.of(
                    new User("organizer", Role.ORGANIZER)
            );
        }

        if (cookie.contains("session=jdg_a_91bc")) {
            return Optional.of(
                    new User("judge_a", Role.JUDGE_A)
            );
        }

        if (cookie.contains("session=jdg_b_44de")) {
            return Optional.of(
                    new User("judge_b", Role.JUDGE_B)
            );
        }

        if (cookie.contains("session=prt_2e88")) {
            return Optional.of(
                    new User("participant", Role.PARTICIPANT)
            );
        }

        if (cookie.contains("session=prt_b_5c3d")) {
            return Optional.of(
                    new User("participant_b", Role.PARTICIPANT)
            );
        }

        return Optional.empty();
    }
}
