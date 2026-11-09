package com.mocktest.controller;

import com.mocktest.dto.Dto;
import com.mocktest.model.Category;
import com.mocktest.model.TestAttempt;
import com.mocktest.model.User;
import com.mocktest.repo.CategoryRepository;
import com.mocktest.service.TestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestControllerTest {
    @Mock TestService service;
    @Mock CategoryRepository categories;
    TestController controller;
    MockHttpSession session;

    @BeforeEach
    void setUp() {
        controller = new TestController(service, categories);
        session = new MockHttpSession();
    }

    private static HttpStatusCode statusOf(Executable e) {
        return assertThrows(ResponseStatusException.class, e).getStatusCode();
    }

    private static TestAttempt attempt(long id, Long userId) {
        TestAttempt a = new TestAttempt();
        a.attemptId = id;
        if (userId != null) {
            a.user = new User();
            a.user.userId = userId;
        }
        return a;
    }

    @SuppressWarnings("unchecked")
    private Set<Long> guestAttempts() {
        return (Set<Long>) session.getAttribute("guestAttempts");
    }

    /** Starts a test as a guest in this session so the attempt is recorded as belonging to it. */
    private void startAsGuest(long attemptId) {
        when(service.start(null)).thenReturn(new Dto.TestView(attemptId, List.of()));
        controller.start(session);
    }

    @Test
    void categoriesListsIdAndName() {
        Category c = new Category();
        c.categoryId = 1L;
        c.categoryName = "Road signs";
        when(categories.findAll()).thenReturn(List.of(c));
        assertEquals(List.of(Map.of("categoryId", 1L, "name", "Road signs")), controller.categories());
    }

    // --- start ---

    @Test
    void guestStartRemembersAttemptInSession() {
        startAsGuest(5);
        assertEquals(Set.of(5L), guestAttempts());
    }

    @Test
    void userStartPassesUserIdAndDoesNotTrackGuestAttempt() {
        session.setAttribute("userId", 3L);
        when(service.start(3L)).thenReturn(new Dto.TestView(5L, List.of()));
        assertEquals(5L, controller.start(session).attemptId());
        assertNull(session.getAttribute("guestAttempts"));
    }

    // --- authorisation of answer / submit / results ---

    @Test
    void guestCanAnswerOwnAttempt() {
        startAsGuest(5);
        when(service.getAttempt(5L)).thenReturn(attempt(5, null));
        var req = new Dto.AnswerRequest(1L, 2L);

        assertEquals(Map.of("status", "saved"), controller.answer(5L, req, session));
        verify(service).answer(5L, req);
    }

    @Test
    void guestCannotAnswerAnotherSessionsAttempt() {
        when(service.getAttempt(5L)).thenReturn(attempt(5, null));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> controller.answer(5L, new Dto.AnswerRequest(1L, 2L), session)));
        verify(service, never()).answer(anyLong(), any());
    }

    @Test
    void guestCannotTouchRegisteredUsersAttempt() {
        when(service.getAttempt(5L)).thenReturn(attempt(5, 3L));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> controller.submit(5L, session)));
        verify(service, never()).submit(anyLong());
    }

    @Test
    void userCanSubmitOwnAttempt() {
        session.setAttribute("userId", 3L);
        when(service.getAttempt(5L)).thenReturn(attempt(5, 3L));
        Dto.Result result = new Dto.Result(5L, 60, 64, true, List.of(), List.of());
        when(service.submit(5L)).thenReturn(result);
        assertSame(result, controller.submit(5L, session));
    }

    @Test
    void userCannotSeeAnotherUsersResults() {
        session.setAttribute("userId", 3L);
        when(service.getAttempt(5L)).thenReturn(attempt(5, 4L));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> controller.results(5L, session)));
        verify(service, never()).results(anyLong());
    }

    @Test
    void guestCanSeeOwnResults() {
        startAsGuest(5);
        when(service.getAttempt(5L)).thenReturn(attempt(5, null));
        Dto.Result result = new Dto.Result(5L, 10, 64, false, List.of(), List.of());
        when(service.results(5L)).thenReturn(result);
        assertSame(result, controller.results(5L, session));
    }

    // --- claim ---

    @Test
    void claimRequiresLogin() {
        assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> controller.claim(5L, session)));
        verifyNoInteractions(service);
    }

    @Test
    void claimOwnAttemptIsAlreadySaved() {
        session.setAttribute("userId", 3L);
        when(service.getAttempt(5L)).thenReturn(attempt(5, 3L));
        assertEquals(Map.of("status", "saved"), controller.claim(5L, session));
        verify(service, never()).claim(anyLong(), anyLong());
    }

    @Test
    void claimAnotherUsersAttemptIsForbidden() {
        session.setAttribute("userId", 3L);
        when(service.getAttempt(5L)).thenReturn(attempt(5, 4L));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> controller.claim(5L, session)));
        verify(service, never()).claim(anyLong(), anyLong());
    }

    @Test
    void claimGuestAttemptFromAnotherSessionIsForbidden() {
        session.setAttribute("userId", 3L);
        when(service.getAttempt(5L)).thenReturn(attempt(5, null));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> controller.claim(5L, session)));
        verify(service, never()).claim(anyLong(), anyLong());
    }

    @Test
    void claimGuestAttemptAfterLoggingInSavesItAndForgetsGuestEntry() {
        startAsGuest(5);
        session.setAttribute("userId", 3L); // logged in after finishing as a guest
        when(service.getAttempt(5L)).thenReturn(attempt(5, null));

        assertEquals(Map.of("status", "saved"), controller.claim(5L, session));

        verify(service).claim(5L, 3L);
        assertFalse(guestAttempts().contains(5L));
    }

    // --- history ---

    @Test
    void historyRequiresLogin() {
        assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> controller.history(session)));
    }

    @Test
    void historyReturnsUsersAttempts() {
        session.setAttribute("userId", 3L);
        List<Dto.AttemptSummary> list = List.of(new Dto.AttemptSummary(5L, null, null, false, null, null));
        when(service.history(3L)).thenReturn(list);
        assertSame(list, controller.history(session));
    }
}
