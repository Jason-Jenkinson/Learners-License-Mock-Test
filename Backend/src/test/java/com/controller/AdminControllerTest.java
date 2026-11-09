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

class AdminControllerTest {
    private QuestionService service;
    private AdminController controller;

    @BeforeEach
    void setUp() {
        service = mock(QuestionService.class);
        controller = new AdminController(service, "test-key");
    }

    private Dto.QuestionRequest sample() {
        return new Dto.QuestionRequest(1L, "Sample?", null, "Because", "test",
                List.of(new Dto.OptionRequest("A", true), new Dto.OptionRequest("B", false)));
    }

    private Question questionWithId(long id) {
        Question q = new Question();
        q.questionId = id;
        return q;
    }

    @Test
    void bulkAdd_importsAllQuestions() {
        when(service.create(any(), any())).thenReturn(questionWithId(1L), questionWithId(2L));

        Map<String, Object> result = controller.bulkAdd("test-key", false, List.of(sample(), sample()));

        assertEquals(2, result.get("imported"));
        assertEquals(List.of(1L, 2L), result.get("questionIds"));
        verify(service, never()).setStatus(anyLong(), anyString());
    }

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

    @Test
    void bulkAdd_verifyTrueMarksQuestionsVerified() {
        when(service.create(any(), any())).thenReturn(questionWithId(1L));

        controller.bulkAdd("test-key", true, List.of(sample()));

        verify(service).setStatus(1L, "Verified");
    }

    @Test
    void bulkAdd_wrongKeyIsRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.bulkAdd("wrong-key", false, List.of(sample())));

        assertEquals(403, ex.getStatusCode().value());
        verifyNoInteractions(service);
    }

    @Test
    void bulkAdd_missingKeyIsRejected() {
        assertThrows(ResponseStatusException.class,
                () -> controller.bulkAdd(null, false, List.of(sample())));
        verifyNoInteractions(service);
    }

    @Test
    void bulkAdd_emptyListImportsNothing() {
        Map<String, Object> result = controller.bulkAdd("test-key", false, List.of());

        assertEquals(0, result.get("imported"));
        verifyNoInteractions(service);
    }

    @Test
    void list_wrongKeyIsRejected() {
        assertThrows(ResponseStatusException.class, () -> controller.list("wrong-key"));
        verifyNoInteractions(service);
    }
}
