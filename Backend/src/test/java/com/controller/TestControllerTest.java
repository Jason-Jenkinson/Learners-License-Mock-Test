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

/**
 * Unit tests for TestController, using a mocked TestService
 * and a map-backed fake session.
 *
 * Covers listing categories, starting a test, the ownership checks that
 * decide who may submit, answer or view results for an attempt (guests by
 * session, registered users by user id), and the login requirement for
 * history. Error cases check the HTTP status the controller reports
 * (403 forbidden, 401 unauthorized).
 */
class TestControllerTest {
    private TestService service;
    private CategoryRepository categories;
    private TestController controller;
    private Map<String, Object> store;
    private HttpSession session;

    /**
     * Creates a mocked service and category repository, builds the
     * TestController under test, and prepares an empty session whose
     * attributes are held in a map so tests can read and set them.
     */
    @BeforeEach
    void setUp() {
        service = mock(TestService.class);
        categories = mock(CategoryRepository.class);
        controller = new TestController(service, categories);
        store = new HashMap<>();
        session = TestData.session(store);
    }

    /**
     * Builds a sample result for the tests to return from the mocked service.
     * @return a passing result with a score of 3 out of 4
     */
    private Dto.Result sampleResult() {
        return new Dto.Result(5L, 3, 4, true, List.of(), List.of());
    }

    /**
     * Creates a guest attempt (no user) with the given id and makes the
     * mocked service return it from getAttempt.
     * @param id the attempt id
     * @return the stubbed guest attempt
     */
    private TestAttempt guestAttempt(long id) {
        TestAttempt a = TestData.attempt(id, null);
        when(service.getAttempt(id)).thenReturn(a);
        return a;
    }

    /**
     * Creates an attempt owned by the given user and makes the mocked service
     * return it from getAttempt.
     * @param id is the attempt id
     * @param userId the id of the user who owns the attempt
     * @return the stubbed user attempt
     */
    private TestAttempt userAttempt(long id, long userId) {
        TestAttempt a = TestData.attempt(id, TestData.user(userId, "ching"));
        when(service.getAttempt(id)).thenReturn(a);
        return a;
    }

    /**
     * Checks that categories returns each category as a map holding
     * its id and name.
     */
    @Test
    void categories_listsIdAndName() {
        when(categories.findAll()).thenReturn(List.of(TestData.category(1, "Road signs")));

        List<Map<String, Object>> result = controller.categories();

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).get("categoryId"));
        assertEquals("Road signs", result.get(0).get("name"));
    }

    /**
     * Checks that when a guest (not logged in) starts a test, the new attempt
     * id is stored in the session's "guestAttempts" set, so the same session
     * can later submit and view that attempt.
     */
    @Test
    void start_guest_remembersAttemptInSession() {
        when(service.start(null)).thenReturn(new Dto.TestView(5L, List.of()));

        controller.start(session);

        @SuppressWarnings("unchecked")
        Set<Long> guestAttempts = (Set<Long>) store.get("guestAttempts");
        assertNotNull(guestAttempts);
        assertTrue(guestAttempts.contains(5L));
    }

    /**
     * Checks that when a logged-in user starts a test, the attempt is not
     * recorded in the session's guest attempts, because it is tied to the
     * user id instead.
     */
    @Test
    void start_loggedInUser_isNotTrackedAsGuest() {
        store.put("userId", 3L);
        when(service.start(3L)).thenReturn(new Dto.TestView(6L, List.of()));

        controller.start(session);

        assertFalse(store.containsKey("guestAttempts"));
    }

    /**
     * Checks that a guest can submit an attempt that their own session
     * started, and gets the service's result back.
     */
    @Test
    void submit_guestOwnAttempt_isAllowed() {
        guestAttempt(5L);
        store.put("guestAttempts", new HashSet<>(Set.of(5L)));
        Dto.Result expected = sampleResult();
        when(service.submit(5L)).thenReturn(expected);

        assertSame(expected, controller.submit(5L, session));
    }

    /**
     * Checks that submitting a guest attempt from a session that did not
     * start it responds with 403 and the service is never asked to submit.
     */
    @Test
    void submit_guestFromAnotherSession_isForbidden() {
        guestAttempt(5L);

        assertStatus(403, () -> controller.submit(5L, session));
        verify(service, never()).submit(anyLong());
    }

    /**
     * Checks that a logged-in user can submit an attempt that belongs to
     * them, and gets the service's result back.
     */
    @Test
    void submit_registeredOwner_isAllowed() {
        userAttempt(5L, 3L);
        store.put("userId", 3L);
        Dto.Result expected = sampleResult();
        when(service.submit(5L)).thenReturn(expected);

        assertSame(expected, controller.submit(5L, session));
    }

    /**
     * Checks that a logged-in user cannot submit an attempt that belongs to a
     * different user: it responds with 403 and the service is never asked to
     * submit.
     */
    @Test
    void submit_registeredAttemptOfAnotherUser_isForbidden() {
        userAttempt(5L, 3L);
        store.put("userId", 4L);

        assertStatus(403, () -> controller.submit(5L, session));
        verify(service, never()).submit(anyLong());
    }

    /**
     * Checks that the owner of an attempt can save an answer, that the
     * controller reports the status "saved", and that the request is passed
     * to the service.
     */
    @Test
    void answer_owner_savesAnswer() {
        guestAttempt(5L);
        store.put("guestAttempts", new HashSet<>(Set.of(5L)));
        Dto.AnswerRequest request = new Dto.AnswerRequest(1L, 2L);

        Map<String, String> result = controller.answer(5L, request, session);

        assertEquals("saved", result.get("status"));
        verify(service).answer(5L, request);
    }

    /**
     * Checks that someone who does not own the attempt cannot save an answer:
     * it responds with 403 and the service is never asked to save anything.
     */
    @Test
    void answer_notOwner_isForbiddenAndNothingSaved() {
        userAttempt(5L, 3L);

        assertStatus(403, () -> controller.answer(5L, new Dto.AnswerRequest(1L, 2L), session));
        verify(service, never()).answer(any(), any());
    }

    /**
     * Checks that a user who does not own the attempt cannot view its
     * results: it responds with 403 and the service is never asked for
     * results.
     */
    @Test
    void results_notOwner_isForbidden() {
        userAttempt(5L, 3L);
        store.put("userId", 4L);

        assertStatus(403, () -> controller.results(5L, session));
        verify(service, never()).results(anyLong());
    }

    /**
     * Checks that the owner of an attempt can view its results, and gets the
     * service's result back.
     */
    @Test
    void results_owner_returnsResult() {
        userAttempt(5L, 3L);
        store.put("userId", 3L);
        Dto.Result expected = sampleResult();
        when(service.results(5L)).thenReturn(expected);

        assertSame(expected, controller.results(5L, session));
    }

    /**
     * Checks that requesting history without being logged in responds with
     * 401 and the service is never asked for history.
     */
    @Test
    void history_notLoggedIn_isUnauthorized() {
        assertStatus(401, () -> controller.history(session));
        verify(service, never()).history(anyLong());
    }

    /**
     * Checks that a logged-in user gets their attempts back from the service
     * as a list of summaries.
     */
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