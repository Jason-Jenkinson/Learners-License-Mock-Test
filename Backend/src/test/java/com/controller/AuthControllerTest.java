package com.mocktest.controller;

import com.mocktest.TestData;
import com.mocktest.dto.Dto;
import com.mocktest.model.User;
import com.mocktest.repo.UserRepository;
import com.mocktest.service.PasswordHasher;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.mocktest.TestData.assertStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    private UserRepository users;
    private AuthController controller;
    private Map<String, Object> store;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        controller = new AuthController(users);
        store = new HashMap<>();
        session = TestData.session(store);
    }

    // ---------- register ----------

    @Test
    void register_shortUsername_isBadRequest() {
        assertStatus(400, () -> controller.register(new Dto.Credentials("ab", "secret1"), session));
        verify(users, never()).save(any());
    }

    @Test
    void register_nullUsername_isBadRequest() {
        assertStatus(400, () -> controller.register(new Dto.Credentials(null, "secret1"), session));
    }

    @Test
    void register_shortPassword_isBadRequest() {
        assertStatus(400, () -> controller.register(new Dto.Credentials("newuser", "12345"), session));
        verify(users, never()).save(any());
    }

    @Test
    void register_usernameTaken_isConflict() {
        when(users.findByUsername("taken")).thenReturn(Optional.of(TestData.user(1, "taken")));

        assertStatus(409, () -> controller.register(new Dto.Credentials("taken", "secret1"), session));
        verify(users, never()).save(any());
    }

    @Test
    void register_success_savesHashedPasswordAndStartsSession() {
        when(users.findByUsername("newuser")).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.userId = 5L;
            return u;
        });

        Map<String, Object> result = controller.register(new Dto.Credentials("  newuser  ", "secret1"), session);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("newuser", saved.username);
        assertNotEquals("secret1", saved.passwordHash);
        assertTrue(PasswordHasher.verify("secret1", saved.passwordHash));
        assertEquals(5L, result.get("userId"));
        assertEquals("newuser", result.get("username"));
        assertEquals(5L, store.get("userId"));
    }

    // ---------- login ----------

    @Test
    void login_correctPassword_startsSession() {
        User u = TestData.user(7, "ching");
        u.passwordHash = PasswordHasher.hash("secret1");
        when(users.findByUsername("ching")).thenReturn(Optional.of(u));

        Map<String, Object> result = controller.login(new Dto.Credentials("ching", "secret1"), session);

        assertEquals(7L, result.get("userId"));
        assertEquals(7L, store.get("userId"));
    }

    @Test
    void login_wrongPassword_isUnauthorized() {
        User u = TestData.user(7, "ching");
        u.passwordHash = PasswordHasher.hash("secret1");
        when(users.findByUsername("ching")).thenReturn(Optional.of(u));

        assertStatus(401, () -> controller.login(new Dto.Credentials("ching", "wrong"), session));
        assertFalse(store.containsKey("userId"));
    }

    @Test
    void login_unknownUser_isUnauthorized() {
        assertStatus(401, () -> controller.login(new Dto.Credentials("nobody", "secret1"), session));
    }

    @Test
    void login_nullCredentials_isUnauthorized() {
        assertStatus(401, () -> controller.login(new Dto.Credentials(null, null), session));
    }

    // ---------- logout and me ----------

    @Test
    void logout_invalidatesSession() {
        Map<String, String> result = controller.logout(session);

        verify(session).invalidate();
        assertEquals("logged out", result.get("status"));
    }

    @Test
    void me_notLoggedIn_isGuest() {
        Map<String, Object> result = controller.me(session);

        assertEquals(true, result.get("guest"));
    }

    @Test
    void me_loggedIn_returnsUser() {
        store.put("userId", 7L);
        when(users.findById(7L)).thenReturn(Optional.of(TestData.user(7, "ching")));

        Map<String, Object> result = controller.me(session);

        assertEquals(7L, result.get("userId"));
        assertEquals("ching", result.get("username"));
    }

    @Test
    void me_sessionUserMissingFromDatabase_isGuest() {
        store.put("userId", 99L);

        Map<String, Object> result = controller.me(session);

        assertEquals(true, result.get("guest"));
    }
}
