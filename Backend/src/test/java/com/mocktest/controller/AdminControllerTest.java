package com.mocktest.controller;

import com.mocktest.dto.Dto;
import com.mocktest.model.Question;
import com.mocktest.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {
    static final String KEY = "test-key";

    @Mock QuestionService service;
    AdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminController(service, KEY);
    }

    private static void assertForbidden(Executable e) {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, e).getStatusCode());
    }

    private static Question question(long id, String status) {
        Question q = new Question();
        q.questionId = id;
        q.verificationStatus = status;
        return q;
    }

    private static Dto.QuestionRequest request() {
        return new Dto.QuestionRequest(1L, "What?", null, null, "PDF",
                List.of(new Dto.OptionRequest("A", true), new Dto.OptionRequest("B", false)));
    }

    @Test
    void everyEndpointRejectsMissingOrWrongKey() {
        for (String bad : new String[]{null, "", "wrong", "TEST-KEY"}) {
            assertForbidden(() -> controller.list(bad));
            assertForbidden(() -> controller.add(bad, request()));
            assertForbidden(() -> controller.verify(bad, 1L, "Verified"));
            assertForbidden(() -> controller.retire(bad, 1L));
            assertForbidden(() -> controller.correct(bad, 1L, request()));
        }
        verifyNoInteractions(service);
    }

    @Test
    void listWithKey() {
        List<Dto.AdminQuestion> list = List.of();
        when(service.list()).thenReturn(list);
        assertSame(list, controller.list(KEY));
    }

    @Test
    void addCreatesUnverifiedQuestion() {
        Dto.QuestionRequest r = request();
        when(service.create(r, null)).thenReturn(question(42, "Unverified"));
        assertEquals(Map.of("questionId", 42L, "status", "Unverified"), controller.add(KEY, r));
    }

    @Test
    void verifySetsRequestedStatus() {
        when(service.setStatus(42L, "Rejected")).thenReturn(question(42, "Rejected"));
        assertEquals(Map.of("questionId", 42L, "status", "Rejected"), controller.verify(KEY, 42L, "Rejected"));
    }

    @Test
    void retireDeactivatesQuestion() {
        assertEquals(Map.of("questionId", 42L, "active", false), controller.retire(KEY, 42L));
        verify(service).retire(42L);
    }

    @Test
    void correctReturnsOldAndNewIds() {
        Dto.QuestionRequest r = request();
        when(service.correct(42L, r)).thenReturn(question(43, "Unverified"));
        assertEquals(Map.of("retiredQuestionId", 42L, "newQuestionId", 43L, "status", "Unverified"),
                controller.correct(KEY, 42L, r));
    }
}
