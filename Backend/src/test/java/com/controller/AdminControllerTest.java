
package com.mocktest.controller;

import com.mocktest.dto.Dto;
import com.mocktest.model.Question;
import com.mocktest.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the AdminController.
 * Tests the bulk question import functionality, API key validation,
 * question verification, and question listing access control.
 */
class AdminControllerTest {

    private QuestionService service;
    private AdminController controller;

    /**
     * Initialises the mocked QuestionService and AdminController
     * before each test.
     */
    @BeforeEach
    void setUp() {
        service = mock(QuestionService.class);
        controller = new AdminController(service, "test-key");
    }

    /**
     * Creates a sample question request containing two answer options.
     * @return a sample QuestionRequest object
     */
    private Dto.QuestionRequest sample() {
        return new Dto.QuestionRequest(1L, "Sample?", null, "Because", "test",
                List.of(new Dto.OptionRequest("A", true), new Dto.OptionRequest("B", false)));
    }

    /**
     * Creates a Question object with the specified ID.
     * @param id the question ID
     * @return a Question object with the assigned ID
     */
    private Question questionWithId(long id) {
        Question q = new Question();
        q.questionId = id;
        return q;
    }

    /**
     * Tests that bulkAdd successfully imports all supplied questions.
     * Verifies that the imported count and question IDs are returned.
     */
    @Test
    void bulkAdd_importsAllQuestions() {
        when(service.create(any(), any())).thenReturn(questionWithId(1L), questionWithId(2L));

        Map<String, Object> result = controller.bulkAdd("test-key", false, List.of(sample(), sample()));

        assertEquals(2, result.get("imported"));
        assertEquals(List.of(1L, 2L), result.get("questionIds"));
        verify(service, never()).setStatus(anyLong(), anyString());
    }

    /**
     * Tests that bulkAdd continues processing questions when one
     * question fails to import.
     * Verifies that successful imports are counted and the failed
     * question's index is included in the response.
     */
    @Test
    @SuppressWarnings("unchecked")
    void bulkAdd_oneBadQuestionDoesNotStopTheRest() {
        when(service.create(any(), any()))
                .thenReturn(questionWithId(1L))
                .thenThrow(new RuntimeException("bad question"))
                .thenReturn(questionWithId(3L));

        Map<String, Object> result = controller.bulkAdd("test-key", false,
                List.of(sample(), sample(), sample()));

        List<Map<String, Object>> failed = (List<Map<String, Object>>) result.get("failed");
        assertEquals(2, result.get("imported"));
        assertEquals(1, failed.size());
        assertEquals(1, failed.get(0).get("index"));
    }

    /**
     * Tests that successfully imported questions are marked as
     * Verified when the verify parameter is true.
     */
    @Test
    void bulkAdd_verifyTrueMarksQuestionsVerified() {
        when(service.create(any(), any())).thenReturn(questionWithId(1L));

        controller.bulkAdd("test-key", true, List.of(sample()));

        verify(service).setStatus(1L, "Verified");
    }

    /**
     * Tests that a bulk import request with an incorrect API key
     * gets rejected with HTTP 403 Forbidden.
     *
     * Verifies that the service is not called.
     */
    @Test
    void bulkAdd_wrongKeyIsRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.bulkAdd("wrong-key", false, List.of(sample())));

        assertEquals(403, ex.getStatusCode().value());
        verifyNoInteractions(service);
    }

    /**
     * Tests that a bulk import request without an API key
     * is rejected.
     *
     * Verifies that the service is not called.
     */
    @Test
    void bulkAdd_missingKeyIsRejected() {
        assertThrows(ResponseStatusException.class,
                () -> controller.bulkAdd(null, false, List.of(sample())));
        verifyNoInteractions(service);
    }

    /**
     * Tests that submitting an empty question list results in
     * 0 imports and no interactions with the service.
     */
    @Test
    void bulkAdd_emptyListImportsNothing() {
        Map<String, Object> result = controller.bulkAdd("test-key", false, List.of());

        assertEquals(0, result.get("imported"));
        verifyNoInteractions(service);
    }

    /**
     * Tests that attempting to list questions with an incorrect
     * API key gets rejected
     * Verifies the service is not called
     */
    @Test
    void list_wrongKeyIsRejected() {
        assertThrows(ResponseStatusException.class, () -> controller.list("wrong-key"));
        verifyNoInteractions(service);
    }
}
