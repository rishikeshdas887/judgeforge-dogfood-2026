package com.dogfood.backend;

import com.dogfood.backend.controller.AuthController;
import com.dogfood.backend.security.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuthControllerTest {

    private final AuthService authService =
            new AuthService();

    private final AuthController controller =
            new AuthController(authService);

    @Test
    void adminLoginCreatesAdminSession() {
        HttpServletResponse response =
                Mockito.mock(HttpServletResponse.class);

        var result =
                controller.login("admin", response);

        assertEquals(200, result.getStatusCode().value());

        ArgumentCaptor<Cookie> captor =
                ArgumentCaptor.forClass(Cookie.class);

        Mockito.verify(response).addCookie(captor.capture());

        Cookie cookie = captor.getValue();

        assertNotNull(cookie);
        assertEquals("session", cookie.getName());
        assertEquals("adm_1a2b", cookie.getValue());
    }
}
