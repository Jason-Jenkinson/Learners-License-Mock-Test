package com.mocktest.service;

import com.mocktest.TestData;
import com.mocktest.dto.Dto;
import com.mocktest.model.AnswerOption;
import com.mocktest.model.Category;
import com.mocktest.model.Question;
import com.mocktest.repo.AnswerOptionRepository;
import com.mocktest.repo.CategoryRepository;
import com.mocktest.repo.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static com.mocktest.TestData.assertStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QuestionService, using mocked repositories.
 * Covers creating questions (success, validation and duplicate checks),
 * changing a question's verification status, retiring and correcting
 * questions, and listing questions with their options.
 * Error cases check the HTTP status the service reports (400 bad request, 404 not found,
 * 409 conflict).
 */
class QuestionServiceTest {
    private QuestionRepository questions;
    private AnswerOptionRepository options;
    private CategoryRepository categories;
    private QuestionService service;
    private Category roadSigns;

    /**
     * Creates fresh mock repositories and the QuestionService under
     * test. Stubs the "Road signs" category lookup, reports no existing
     * questions with the same text by default, and makes save return
     * the question it receives.
     */
    @BeforeEach
    void setUp() {
        questions = mock(QuestionRepository.class);
        options = mock(AnswerOptionRepository.class);
        categories = mock(CategoryRepository.class);
        service = new QuestionService(questions, options, categories);

        roadSigns = TestData.category(1, "Road signs");
        when(categories.findById(1L)).thenReturn(Optional.of(roadSigns));
        when(questions.findByQuestionTextIgnoreCase(anyString())).thenReturn(List.of());
        when(questions.save(any(Question.class))).thenAnswer(i -> i.getArgument(0));
    }

    /**
     * Builds a valid option list with exactly one correct option.
     * @return the options "A" (correct) and "B" (incorrect)
     */
    private List<Dto.OptionRequest> goodOptions() {
        return List.of(new Dto.OptionRequest("A", true), new Dto.OptionRequest("B", false));
    }

    /**
     * Builds a question request for the "Road signs" category with the given
     * text, source and options.
     * @param text   the question text
     * @param source the source of the question
     * @param opts   the answer options
     * @return the question request
     */
    private Dto.QuestionRequest request(String text, String source, List<Dto.OptionRequest> opts) {
        return new Dto.QuestionRequest(1L, text, null, "Because", source, opts);
    }

    /**
     * Builds a request that passes all validation.
     * @return a valid question request
     */
    private Dto.QuestionRequest validRequest() {
        return request("What is X?", "NaTIS manual", goodOptions());
    }

    /**
     * Checks that creating a question from a valid request trims the text and
     * saves it as an active question with the status "Unverified", linked to
     * the requested category and carrying the given source.
     */
    @Test
    void create_validRequest_savesUnverifiedActiveQuestion() {
        Question q = service.create(request("  What is X?  ", "NaTIS manual", goodOptions()), null);

        assertEquals("What is X?", q.questionText);
        assertEquals("Unverified", q.verificationStatus);
        assertTrue(q.active);
        assertSame(roadSigns, q.category);
        assertEquals("NaTIS manual", q.source);
    }

    /**
     * Checks that every option in the request is saved, in order, with the
     * correct flag and text, and linked to the created question.
     */
    @Test
    void create_validRequest_savesEveryOption() {
        Question q = service.create(validRequest(), null);

        ArgumentCaptor<AnswerOption> captor = ArgumentCaptor.forClass(AnswerOption.class);
        verify(options, times(2)).save(captor.capture());
        List<AnswerOption> saved = captor.getAllValues();
        assertTrue(saved.get(0).correct);
        assertFalse(saved.get(1).correct);
        assertSame(q, saved.get(0).question);
        assertEquals("A", saved.get(0).optionText);
    }

    /**
     * Checks that a question whose text is blank responds with 400 and is not
     * saved.
     */
    @Test
    void create_blankText_isBadRequest() {
        assertStatus(400, () -> service.create(request("   ", "src", goodOptions()), null));
        verify(questions, never()).save(any());
    }

    /**
     * Checks that a question whose text is null responds with 400.
     */
    @Test
    void create_nullText_isBadRequest() {
        assertStatus(400, () -> service.create(request(null, "src", goodOptions()), null));
    }

    /**
     * Checks that a question with a blank source responds with 400 and is not
     * saved, because every question must name its source.
     */
    @Test
    void create_missingSource_isBadRequest() {
        assertStatus(400, () -> service.create(request("What is X?", " ", goodOptions()), null));
        verify(questions, never()).save(any());
    }

    /**
     * Checks that a question with fewer than two options responds with 400 and
     * no options are saved.
     */
    @Test
    void create_fewerThanTwoOptions_isBadRequest() {
        assertStatus(400, () -> service.create(
                request("What is X?", "src", List.of(new Dto.OptionRequest("A", true))), null));
        verify(options, never()).save(any());
    }

    /**
     * Checks that a question with no correct option responds with 400.
     */
    @Test
    void create_noCorrectOption_isBadRequest() {
        assertStatus(400, () -> service.create(request("What is X?", "src",
                List.of(new Dto.OptionRequest("A", false), new Dto.OptionRequest("B", false))), null));
    }

    /**
     * Checks that a question with more than one correct option responds with
     * 400, because exactly one option must be correct.
     */
    @Test
    void create_twoCorrectOptions_isBadRequest() {
        assertStatus(400, () -> service.create(request("What is X?", "src",
                List.of(new Dto.OptionRequest("A", true), new Dto.OptionRequest("B", true))), null));
    }

    /**
     * Checks that a request for a category id that does not exist responds
     * with 400 and the question is not saved.
     */
    @Test
    void create_unknownCategory_isBadRequest() {
        Dto.QuestionRequest r = new Dto.QuestionRequest(99L, "What is X?", null, "Because", "src", goodOptions());

        assertStatus(400, () -> service.create(r, null));
        verify(questions, never()).save(any());
    }

    /**
     * Checks that creating a question whose text matches an active question
     *responds with 409 and is not saved.
     */
    @Test
    void create_duplicateOfActiveQuestion_isConflict() {
        Question existing = TestData.question(10, roadSigns);
        when(questions.findByQuestionTextIgnoreCase("What is X?")).thenReturn(List.of(existing));

        assertStatus(409, () -> service.create(validRequest(), null));
        verify(questions, never()).save(any());
    }

    /**
     * Checks that a question whose text matches only a retired (inactive)
     * question is allowed.
     */
    @Test
    void create_duplicateOfRetiredQuestion_isAllowed() {
        Question existing = TestData.question(10, roadSigns);
        existing.active = false;
        when(questions.findByQuestionTextIgnoreCase("What is X?")).thenReturn(List.of(existing));

        assertDoesNotThrow(() -> service.create(validRequest(), null));
    }

    /**
     * Checks that the question being corrected is ignored by the duplicate
     * check, so a correction may keep the same text as the question it
     * replaces.
     */
    @Test
    void create_duplicateOfQuestionBeingCorrected_isAllowed() {
        Question existing = TestData.question(10, roadSigns);
        when(questions.findByQuestionTextIgnoreCase("What is X?")).thenReturn(List.of(existing));

        assertDoesNotThrow(() -> service.create(validRequest(), 10L));
    }

    /**
     * Checks that setting a valid verification status updates the question
     * and saves it.
     */
    @Test
    void setStatus_validStatus_updatesQuestion() {
        Question q = TestData.question(1, roadSigns);
        when(questions.findById(1L)).thenReturn(Optional.of(q));

        Question result = service.setStatus(1L, "Verified");

        assertEquals("Verified", result.verificationStatus);
        verify(questions).save(q);
    }

    /**
     * Checks that a status that is not an allowed value responds with 400 and
     * nothing is saved.
     */
    @Test
    void setStatus_invalidStatus_isBadRequest() {
        assertStatus(400, () -> service.setStatus(1L, "Maybe"));
        verify(questions, never()).save(any());
    }

    /**
     * Checks that setting the status of a question that does not exist
     * responds with 404.
     */
    @Test
    void setStatus_unknownQuestion_isNotFound() {
        assertStatus(404, () -> service.setStatus(42L, "Verified"));
    }

    /**
     * Checks that retiring a question marks it inactive and saves it.
     */
    @Test
    void retire_marksQuestionInactive() {
        Question q = TestData.question(1, roadSigns);
        when(questions.findById(1L)).thenReturn(Optional.of(q));

        Question result = service.retire(1L);

        assertFalse(result.active);
        verify(questions).save(q);
    }

    /**
     * Checks that correcting a question retires the old question and returns
     * a different, new question that is active and "Unverified".
     */
    @Test
    void correct_retiresOldQuestionAndCreatesNewUnverifiedOne() {
        Question old = TestData.question(1, roadSigns);
        old.questionText = "What is X?";
        when(questions.findById(1L)).thenReturn(Optional.of(old));
        when(questions.findByQuestionTextIgnoreCase("What is X?")).thenReturn(List.of(old));

        Question fresh = service.correct(1L, validRequest());

        assertFalse(old.active);
        assertNotSame(old, fresh);
        assertTrue(fresh.active);
        assertEquals("Unverified", fresh.verificationStatus);
        verify(questions).save(old);
    }

    /**
     * Checks that {list} returns each question with its category name
     * and its options, including the correct flag of each option.
     */
    @Test
    void list_returnsQuestionsWithTheirOptions() {
        Question q = TestData.question(1, roadSigns);
        AnswerOption right = TestData.option(11, q, true);
        AnswerOption wrong = TestData.option(12, q, false);
        when(questions.findAll()).thenReturn(List.of(q));
        when(options.findByQuestion_QuestionId(1L)).thenReturn(List.of(right, wrong));

        List<Dto.AdminQuestion> result = service.list();

        assertEquals(1, result.size());
        assertEquals("Road signs", result.get(0).category());
        assertEquals(2, result.get(0).options().size());
        assertTrue(result.get(0).options().get(0).correct());
    }
}