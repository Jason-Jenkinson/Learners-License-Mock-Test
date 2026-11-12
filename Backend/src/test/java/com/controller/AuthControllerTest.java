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

/**
 * Unit tests for AuthController, using a mocked
 * UserRepository and a map-backed fake session.
 * Covers registering a user, logging in, logging out, and looking up the
 * current user. Error cases check the HTTP status the controller reports
 * (400 bad request, 409 conflict, 401 unauthorized).
 */
class AuthControllerTest {
    private UserRepository users;
    private AuthController controller;
    private Map<String, Object> store;
    private HttpSession session;

    /**
     * Creates a mocked user repository, builds the {@link AuthController}
     * under test, and prepares an empty session whose attributes are held in a
     * map so tests can read and set them.
     */
    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        controller = new AuthController(users);
        store = new HashMap<>();
        session = TestData.session(store);
    }

    /**
     * Checks that registering with a username that is too short (2
     * characters) responds with 400 and no user is saved.
     */
    @Test
    void register_shortUsername_isBadRequest() {
        assertStatus(400, () -> controller.register(new Dto.Credentials("ab", "secret1"), session));
        verify(users, never()).save(any());
    }

    /**
     * Checks that registering with a null username responds with 400.
     */
    @Test
    void register_nullUsername_isBadRequest() {
        assertStatus(400, () -> controller.register(new Dto.Credentials(null, "secret1"), session));
    }

    /**
     * Checks that registering with a password that is too short (5
     * characters) responds with 400 and no user is saved.
     */
    @Test
    void register_shortPassword_isBadRequest() {
        assertStatus(400, () -> controller.register(new Dto.Credentials("newuser", "12345"), session));
        verify(users, never()).save(any());
    }

    /**
     * Checks that registering with a username that already exists responds
     * with 409 and no user is saved.
     */
    @Test
    void register_usernameTaken_isConflict() {
        when(users.findByUsername("taken")).thenReturn(Optional.of(TestData.user(1, "taken")));

        assertStatus(409, () -> controller.register(new Dto.Credentials("taken", "secret1"), session));
        verify(users, never()).save(any());
    }

    /**
     * Checks that a successful registration trims the username, saves a
     * hashed password that verifies against the original,
     * returns the new user id and username, and logs the user in by storing
     * the user id in the session.
     */
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

    /**
     * Checks that logging in with the correct password returns the user id
     * and stores it in the session.
     */
    @Test
    void login_correctPassword_startsSession() {
        User u = TestData.user(7, "ching");
        u.passwordHash = PasswordHasher.hash("secret1");
        when(users.findByUsername("ching")).thenReturn(Optional.of(u));

        Map<String, Object> result = controller.login(new Dto.Credentials("ching", "secret1"), session);

        assertEquals(7L, result.get("userId"));
        assertEquals(7L, store.get("userId"));
    }

    /**
     * Checks that logging in with the wrong password responds with 401 and
     * does not store a user id in the session.
     */
    @Test
    void login_wrongPassword_isUnauthorized() {
        User u = TestData.user(7, "ching");
        u.passwordHash = PasswordHasher.hash("secret1");
        when(users.findByUsername("ching")).thenReturn(Optional.of(u));

        assertStatus(401, () -> controller.login(new Dto.Credentials("ching", "wrong"), session));
        assertFalse(store.containsKey("userId"));
    }

    /**
     * Checks that logging in with a username that does not exist responds
     * with 401.
     */
    @Test
    void login_unknownUser_isUnauthorized() {
        assertStatus(401, () -> controller.login(new Dto.Credentials("nobody", "secret1"), session));
    }

    /**
     * Checks that logging in with a null username and password responds with
     * 401.
     */
    @Test
    void login_nullCredentials_isUnauthorized() {
        assertStatus(401, () -> controller.login(new Dto.Credentials(null, null), session));
    }

    /**
     * Checks that logging out invalidates the session and reports the status
     * "logged out".
     */
    @Test
    void logout_invalidatesSession() {
        Map<String, String> result = controller.logout(session);

        verify(session).invalidate();
        assertEquals("logged out", result.get("status"));
    }

    /**
     * Checks that it reports the visitor as a guest when no user is
     * logged in.
     */
    @Test
    void me_notLoggedIn_isGuest() {
        Map<String, Object> result = controller.me(session);

        assertEquals(true, result.get("guest"));
    }

    /**
     * Checks that it  returns the id and username of the logged-in
     * user.
     */
    @Test
    void me_loggedIn_returnsUser() {
        store.put("userId", 7L);
        when(users.findById(7L)).thenReturn(Optional.of(TestData.user(7, "ching")));

        Map<String, Object> result = controller.me(session);

        assertEquals(7L, result.get("userId"));
        assertEquals("ching", result.get("username"));
    }

    /**
     * Checks that it reports the visitor as a guest when the user id
     * stored in the session no longer exists in the database.
     */
    @Test
    void me_sessionUserMissingFromDatabase_isGuest() {
        store.put("userId", 99L);

        Map<String, Object> result = controller.me(session);

        assertEquals(true, result.get("guest"));
    }
}