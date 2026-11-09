package com.mocktest.service;

import com.mocktest.dto.Dto;
import com.mocktest.model.AnswerOption;
import com.mocktest.model.Category;
import com.mocktest.model.Question;
import com.mocktest.repo.AnswerOptionRepository;
import com.mocktest.repo.CategoryRepository;
import com.mocktest.repo.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {
    @Mock QuestionRepository questions;
    @Mock AnswerOptionRepository options;
    @Mock CategoryRepository categories;

    QuestionService service;
    Category signs;

    @BeforeEach
    void setUp() {
        service = new QuestionService(questions, options, categories);
        signs = new Category();
        signs.categoryId = 2L;
        signs.categoryName = "Road signs";
    }

    private static HttpStatusCode statusOf(Executable e) {
        return assertThrows(ResponseStatusException.class, e).getStatusCode();
    }

    private static Dto.QuestionRequest request(String text, String source, List<Dto.OptionRequest> opts) {
        return new Dto.QuestionRequest(2L, text, "img.png", "Because.", source, opts);
    }

    private static List<Dto.OptionRequest> twoOptions() {
        return List.of(new Dto.OptionRequest("Stop", true), new Dto.OptionRequest("Go", false));
    }

    private static Question question(long id, String text, boolean active) {
        Question q = new Question();
        q.questionId = id;
        q.questionText = text;
        q.active = active;
        return q;
    }

    private void saveAssignsId(long id) {
        when(questions.save(any(Question.class))).thenAnswer(inv -> {
            Question q = inv.getArgument(0);
            if (q.questionId == null) q.questionId = id;
            return q;
        });
    }

    // --- create: validation ---

    @Test
    void createRejectsBlankText() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.create(request("  ", "PDF", twoOptions()), null)));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.create(request(null, "PDF", twoOptions()), null)));
        verifyNoInteractions(questions, options);
    }

    @Test
    void createRejectsMissingSource() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.create(request("What?", " ", twoOptions()), null)));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.create(request("What?", null, twoOptions()), null)));
    }

    @Test
    void createRejectsFewerThanTwoOptions() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.create(request("What?", "PDF", List.of(new Dto.OptionRequest("Stop", true))), null)));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.create(request("What?", "PDF", null), null)));
    }

    @Test
    void createRequiresExactlyOneCorrectOption() {
        var none = List.of(new Dto.OptionRequest("A", false), new Dto.OptionRequest("B", false));
        var two = List.of(new Dto.OptionRequest("A", true), new Dto.OptionRequest("B", true));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.create(request("What?", "PDF", none), null)));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.create(request("What?", "PDF", two), null)));
    }

    @Test
    void createRejectsUnknownCategory() {
        when(categories.findById(2L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.create(request("What?", "PDF", twoOptions()), null)));
    }

    // --- create: duplicates ---

    @Test
    void createRejectsDuplicateOfActiveQuestion() {
        when(categories.findById(2L)).thenReturn(Optional.of(signs));
        when(questions.findByQuestionTextIgnoreCase("What?")).thenReturn(List.of(question(7, "what?", true)));
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.create(request("  What?  ", "PDF", twoOptions()), null)));
        verify(questions, never()).save(any());
    }

    @Test
    void createAllowsSameTextAsRetiredQuestion() {
        when(categories.findById(2L)).thenReturn(Optional.of(signs));
        when(questions.findByQuestionTextIgnoreCase("What?")).thenReturn(List.of(question(7, "What?", false)));
        saveAssignsId(50);
        assertEquals(50L, service.create(request("What?", "PDF", twoOptions()), null).questionId);
    }

    @Test
    void createIgnoresDuplicateOfTheQuestionBeingCorrected() {
        when(categories.findById(2L)).thenReturn(Optional.of(signs));
        when(questions.findByQuestionTextIgnoreCase("What?")).thenReturn(List.of(question(7, "What?", true)));
        saveAssignsId(50);
        assertEquals(50L, service.create(request("What?", "PDF", twoOptions()), 7L).questionId);
    }

    // --- create: success ---

    @Test
    void createSavesUnverifiedQuestionWithItsOptions() {
        when(categories.findById(2L)).thenReturn(Optional.of(signs));
        when(questions.findByQuestionTextIgnoreCase("What does this sign mean?")).thenReturn(List.of());
        saveAssignsId(50);

        Question q = service.create(request("  What does this sign mean?  ", "PDF", twoOptions()), null);

        assertEquals(50L, q.questionId);
        assertEquals("What does this sign mean?", q.questionText);
        assertEquals("Unverified", q.verificationStatus);
        assertTrue(q.active);
        assertSame(signs, q.category);
        assertEquals("img.png", q.imagePath);
        assertEquals("Because.", q.explanation);
        assertEquals("PDF", q.source);

        ArgumentCaptor<AnswerOption> saved = ArgumentCaptor.forClass(AnswerOption.class);
        verify(options, times(2)).save(saved.capture());
        List<AnswerOption> opts = saved.getAllValues();
        assertEquals("Stop", opts.get(0).optionText);
        assertTrue(opts.get(0).correct);
        assertEquals("Go", opts.get(1).optionText);
        assertFalse(opts.get(1).correct);
        opts.forEach(o -> assertSame(q, o.question));
    }

    // --- get / setStatus / retire ---

    @Test
    void getMissingQuestionIs404() {
        when(questions.findById(99L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.get(99L)));
    }

    @Test
    void setStatusRejectsUnknownStatus() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.setStatus(1L, "Approved")));
        verifyNoInteractions(questions);
    }

    @Test
    void setStatusUpdatesQuestion() {
        Question q = question(1, "What?", true);
        when(questions.findById(1L)).thenReturn(Optional.of(q));
        when(questions.save(q)).thenReturn(q);
        for (String s : List.of("Verified", "Rejected", "Unverified")) {
            assertEquals(s, service.setStatus(1L, s).verificationStatus);
        }
    }

    @Test
    void retireDeactivatesQuestion() {
        Question q = question(1, "What?", true);
        when(questions.findById(1L)).thenReturn(Optional.of(q));
        when(questions.save(q)).thenReturn(q);
        assertFalse(service.retire(1L).active);
    }

    // --- correct ---

    @Test
    void correctRetiresOldQuestionAndCreatesNewOne() {
        Question old = question(7, "What?", true);
        when(questions.findById(7L)).thenReturn(Optional.of(old));
        when(categories.findById(2L)).thenReturn(Optional.of(signs));
        when(questions.findByQuestionTextIgnoreCase("What?")).thenReturn(List.of(old));
        saveAssignsId(50);

        Question fresh = service.correct(7L, request("What?", "PDF", twoOptions()));

        assertEquals(50L, fresh.questionId);
        assertTrue(fresh.active);
        assertFalse(old.active);
        verify(questions).save(old);
    }

    @Test
    void correctMissingQuestionIs404AndCreatesNothing() {
        when(questions.findById(7L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.correct(7L, request("What?", "PDF", twoOptions()))));
        verify(questions, never()).save(any());
    }

    // --- list ---

    @Test
    void listIncludesCategoryStatusAndOptions() {
        Question q = question(1, "What?", false);
        q.category = signs;
        q.source = "PDF";
        q.verificationStatus = "Verified";
        AnswerOption o = new AnswerOption();
        o.optionId = 10L;
        o.optionText = "Stop";
        o.correct = true;
        when(questions.findAll()).thenReturn(List.of(q));
        when(options.findByQuestion_QuestionId(1L)).thenReturn(List.of(o));

        List<Dto.AdminQuestion> list = service.list();

        assertEquals(List.of(new Dto.AdminQuestion(1L, "Road signs", "What?", "PDF", "Verified", false,
                List.of(new Dto.AdminOption(10L, "Stop", true)))), list);
    }
}
