package com.mocktest.controller;

import com.mocktest.TestData;
import com.mocktest.dto.Dto;
import com.mocktest.model.TestAttempt;
import com.mocktest.repo.CategoryRepository;
import com.mocktest.service.TestService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static com.mocktest.TestData.assertStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestControllerTest {
    private TestService service;
    private CategoryRepository categories;
    private TestController controller;
    private Map<String, Object> store;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        service = mock(TestService.class);
        categories = mock(CategoryRepository.class);
        controller = new TestController(service, categories);
        store = new HashMap<>();
        session = TestData.session(store);
    }

    private Dto.Result sampleResult() {
        return new Dto.Result(5L, 3, 4, true, List.of(), List.of());
    }

    private TestAttempt guestAttempt(long id) {
        TestAttempt a = TestData.attempt(id, null);
        when(service.getAttempt(id)).thenReturn(a);
        return a;
    }

    private TestAttempt userAttempt(long id, long userId) {
        TestAttempt a = TestData.attempt(id, TestData.user(userId, "ching"));
        when(service.getAttempt(id)).thenReturn(a);
        return a;
    }

    // ---------- categories ----------

    @Test
    void categories_listsIdAndName() {
        when(categories.findAll()).thenReturn(List.of(TestData.category(1, "Road signs")));

        List<Map<String, Object>> result = controller.categories();

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).get("categoryId"));
        assertEquals("Road signs", result.get(0).get("name"));
    }

    // ---------- start ----------

    @Test
    void start_guest_remembersAttemptInSession() {
        when(service.start(null)).thenReturn(new Dto.TestView(5L, List.of()));

        controller.start(session);

        @SuppressWarnings("unchecked")
        Set<Long> guestAttempts = (Set<Long>) store.get("guestAttempts");
        assertNotNull(guestAttempts);
        assertTrue(guestAttempts.contains(5L));
    }

    @Test
    void start_loggedInUser_isNotTrackedAsGuest() {
        store.put("userId", 3L);
        when(service.start(3L)).thenReturn(new Dto.TestView(6L, List.of()));

        controller.start(session);

        assertFalse(store.containsKey("guestAttempts"));
    }

    // ---------- ownership checks ----------

    @Test
    void submit_guestOwnAttempt_isAllowed() {
        guestAttempt(5L);
        store.put("guestAttempts", new HashSet<>(Set.of(5L)));
        Dto.Result expected = sampleResult();
        when(service.submit(5L)).thenReturn(expected);

        assertSame(expected, controller.submit(5L, session));
    }

    @Test
    void submit_guestFromAnotherSession_isForbidden() {
        guestAttempt(5L);

        assertStatus(403, () -> controller.submit(5L, session));
        verify(service, never()).submit(anyLong());
    }

    @Test
    void submit_registeredOwner_isAllowed() {
        userAttempt(5L, 3L);
        store.put("userId", 3L);
        Dto.Result expected = sampleResult();
        when(service.submit(5L)).thenReturn(expected);

        assertSame(expected, controller.submit(5L, session));
    }

    @Test
    void submit_registeredAttemptOfAnotherUser_isForbidden() {
        userAttempt(5L, 3L);
        store.put("userId", 4L);

        assertStatus(403, () -> controller.submit(5L, session));
        verify(service, never()).submit(anyLong());
    }

    @Test
    void answer_owner_savesAnswer() {
        guestAttempt(5L);
        store.put("guestAttempts", new HashSet<>(Set.of(5L)));
        Dto.AnswerRequest request = new Dto.AnswerRequest(1L, 2L);

        Map<String, String> result = controller.answer(5L, request, session);

        assertEquals("saved", result.get("status"));
        verify(service).answer(5L, request);
    }

    @Test
    void answer_notOwner_isForbiddenAndNothingSaved() {
        userAttempt(5L, 3L);

        assertStatus(403, () -> controller.answer(5L, new Dto.AnswerRequest(1L, 2L), session));
        verify(service, never()).answer(any(), any());
    }

    @Test
    void results_notOwner_isForbidden() {
        userAttempt(5L, 3L);
        store.put("userId", 4L);

        assertStatus(403, () -> controller.results(5L, session));
        verify(service, never()).results(anyLong());
    }

    @Test
    void results_owner_returnsResult() {
        userAttempt(5L, 3L);
        store.put("userId", 3L);
        Dto.Result expected = sampleResult();
        when(service.results(5L)).thenReturn(expected);

        assertSame(expected, controller.results(5L, session));
    }

    // ---------- history ----------

    @Test
    void history_notLoggedIn_isUnauthorized() {
        assertStatus(401, () -> controller.history(session));
        verify(service, never()).history(anyLong());
    }

    @Test
    void history_loggedIn_returnsAttempts() {
        store.put("userId", 3L);
        when(service.history(3L)).thenReturn(List.of(
                new Dto.AttemptSummary(1L, LocalDateTime.now(), null, false, null, null)));

        List<Dto.AttemptSummary> result = controller.history(session);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).attemptId());
    }
}
