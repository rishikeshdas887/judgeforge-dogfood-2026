package com.dogfood.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    public enum Role {
        ORGANIZER,
        JUDGE_A,
        JUDGE_B,
        PARTICIPANT
    }

    public record User(String id, Role role) {}

    public Optional<User> currentUser(HttpServletRequest request) {
        String cookie = request.getHeader("Cookie");

        if (cookie == null || cookie.isBlank()) {
            return Optional.empty();
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

        return Optional.empty();
    }
}
