package com.mocktest.controller;

import com.mocktest.dto.Dto;
import com.mocktest.model.User;
import com.mocktest.repo.UserRepository;
import com.mocktest.service.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
    @Mock UserRepository users;
    AuthController controller;
    MockHttpSession session;

    @BeforeEach
    void setUp() {
        controller = new AuthController(users);
        session = new MockHttpSession();
    }

    private static HttpStatusCode statusOf(Executable e) {
        return assertThrows(ResponseStatusException.class, e).getStatusCode();
    }

    private static User stored(long id, String name, String password) {
        User u = new User();
        u.userId = id;
        u.username = name;
        u.passwordHash = PasswordHasher.hash(password);
        return u;
    }

    // --- register ---

    @Test
    void registerRejectsShortOrMissingUsername() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.register(new Dto.Credentials(" ab ", "secret1"), session)));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.register(new Dto.Credentials(null, "secret1"), session)));
        verifyNoInteractions(users);
    }

    @Test
    void registerRejectsShortOrMissingPassword() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.register(new Dto.Credentials("thandi", "12345"), session)));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.register(new Dto.Credentials("thandi", null), session)));
        verifyNoInteractions(users);
    }

    @Test
    void registerRejectsTakenUsername() {
        when(users.findByUsername("thandi")).thenReturn(Optional.of(stored(1, "thandi", "secret1")));
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> controller.register(new Dto.Credentials(" thandi ", "secret1"), session)));
        verify(users, never()).save(any());
    }

    @Test
    void registerSavesHashedPasswordAndLogsIn() {
        when(users.findByUsername("thandi")).thenReturn(Optional.empty());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        when(users.save(saved.capture())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.userId = 8L;
            return u;
        });

        Map<String, Object> body = controller.register(new Dto.Credentials(" thandi ", "secret1"), session);

        assertEquals(Map.of("userId", 8L, "username", "thandi"), body);
        assertEquals(8L, session.getAttribute("userId"));
        User u = saved.getValue();
        assertEquals("thandi", u.username);
        assertNotEquals("secret1", u.passwordHash);
        assertTrue(PasswordHasher.verify("secret1", u.passwordHash));
    }

    // --- login ---

    @Test
    void loginWithCorrectPasswordSetsSession() {
        when(users.findByUsername("thandi")).thenReturn(Optional.of(stored(8, "thandi", "secret1")));

        Map<String, Object> body = controller.login(new Dto.Credentials(" thandi ", "secret1"), session);

        assertEquals(Map.of("userId", 8L, "username", "thandi"), body);
        assertEquals(8L, session.getAttribute("userId"));
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() {
        when(users.findByUsername("thandi")).thenReturn(Optional.of(stored(8, "thandi", "secret1")));
        assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> controller.login(new Dto.Credentials("thandi", "wrong!"), session)));
        assertNull(session.getAttribute("userId"));
    }

    @Test
    void loginWithUnknownUserIsUnauthorized() {
        when(users.findByUsername("nobody")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> controller.login(new Dto.Credentials("nobody", "secret1"), session)));
    }

    @Test
    void loginWithMissingFieldsIsUnauthorizedNotAnError() {
        when(users.findByUsername("")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> controller.login(new Dto.Credentials(null, null), session)));
    }

    // --- logout / me ---

    @Test
    void logoutInvalidatesSession() {
        session.setAttribute("userId", 8L);
        assertEquals(Map.of("status", "logged out"), controller.logout(session));
        assertTrue(session.isInvalid());
    }

    @Test
    void meIsGuestWhenNotLoggedIn() {
        assertEquals(Map.of("guest", true), controller.me(session));
        verifyNoInteractions(users);
    }

    @Test
    void meReturnsLoggedInUser() {
        session.setAttribute("userId", 8L);
        when(users.findById(8L)).thenReturn(Optional.of(stored(8, "thandi", "secret1")));
        assertEquals(Map.of("userId", 8L, "username", "thandi"), controller.me(session));
    }

    @Test
    void meIsGuestWhenSessionUserNoLongerExists() {
        session.setAttribute("userId", 8L);
        when(users.findById(8L)).thenReturn(Optional.empty());
        assertEquals(Map.of("guest", true), controller.me(session));
    }
}
