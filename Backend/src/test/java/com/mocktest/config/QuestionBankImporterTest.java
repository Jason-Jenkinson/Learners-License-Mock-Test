package com.mocktest.config;

import com.mocktest.model.AnswerOption;
import com.mocktest.model.Category;
import com.mocktest.model.Question;
import com.mocktest.repo.AnswerOptionRepository;
import com.mocktest.repo.CategoryRepository;
import com.mocktest.repo.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionBankImporterTest {
    @Mock QuestionRepository questions;
    @Mock AnswerOptionRepository options;
    @Mock CategoryRepository categories;
    @TempDir Path dir;

    final List<Question> savedQuestions = new ArrayList<>();
    final List<AnswerOption> savedOptions = new ArrayList<>();
    long nextId = 1;

    @BeforeEach
    void setUp() {
        lenient().when(categories.findAll()).thenReturn(List.of(
                category(1, "Rules of the road"), category(2, "Road signs"), category(3, "Controls of the vehicle")));
        lenient().when(questions.findSourcesStartingWith(QuestionBankImporter.SOURCE_PREFIX)).thenReturn(List.of());
        lenient().when(questions.findAll()).thenReturn(List.of());
        lenient().when(questions.findBySourceAndActiveTrue("Sample data")).thenReturn(List.of());
        lenient().when(questions.save(any(Question.class))).thenAnswer(inv -> {
            Question q = inv.getArgument(0);
            if (q.questionId == null) q.questionId = nextId++;
            savedQuestions.add(q);
            return q;
        });
        lenient().when(options.save(any(AnswerOption.class))).thenAnswer(inv -> {
            savedOptions.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    private static Category category(long id, String name) {
        Category c = new Category();
        c.categoryId = id;
        c.categoryName = name;
        return c;
    }

    /** Builds one JSON question entry. Pass null for an answer to leave it out. */
    private static String entry(int number, String category, String text, String correct,
                                String a, String b, String c, String d) {
        return """
                {"question_number": %d, "category": %s, "question_text": %s, "reference_image": null,
                 "correct_answer": %s, "answer_text_a": %s, "answer_text_b": %s, "answer_text_c": %s,
                 "answer_text_d": %s, "answer_information_text": "Info %d"}"""
                .formatted(number, json(category), json(text), json(correct), json(a), json(b), json(c), json(d), number);
    }

    private static String json(String s) {
        return s == null ? "null" : "\"" + s.replace("\"", "\\\"") + "\"";
    }

    private void runWith(String... entries) throws Exception {
        Path file = dir.resolve("bank.json");
        Files.writeString(file, "[" + String.join(",", entries) + "]");
        // First location doesn't exist, so the importer must fall back to the second (padded with spaces).
        importer(List.of(dir.resolve("missing.json").toUri().toString(), " " + file.toUri() + " ")).run();
    }

    private QuestionBankImporter importer(List<String> locations) {
        return new QuestionBankImporter(questions, options, categories, new DefaultResourceLoader(), locations);
    }

    private List<Question> imported() {
        return savedQuestions.stream().filter(q -> q.source.startsWith(QuestionBankImporter.SOURCE_PREFIX)).toList();
    }

    @Test
    void importsValidQuestionAsVerifiedWithCorrectOptionMarked() throws Exception {
        runWith(entry(12, "Road signs", "  What does a red octagon mean?  ", "B. Stop",
                "Yield", "Stop", "Go", null));

        assertEquals(1, imported().size());
        Question q = imported().get(0);
        assertEquals("What does a red octagon mean?", q.questionText);
        assertEquals("Road signs", q.category.categoryName);
        assertEquals("Learner's licence question bank PDF, Q12", q.source);
        assertEquals("Verified", q.verificationStatus);
        assertTrue(q.active);
        assertNull(q.imagePath);
        assertEquals("Info 12", q.explanation);

        assertEquals(3, savedOptions.size()); // empty option D is left out
        assertEquals(List.of("Yield", "Stop", "Go"), savedOptions.stream().map(o -> o.optionText).toList());
        assertEquals(List.of(false, true, false), savedOptions.stream().map(o -> o.correct).toList());
        savedOptions.forEach(o -> assertSame(q, o.question));
    }

    @Test
    void categoryAndAnswerLetterAreCaseInsensitive() throws Exception {
        runWith(entry(1, "ROAD SIGNS", "Q one", "c) Go", "Yield", "Stop", "Go", null));
        assertEquals(1, imported().size());
        assertTrue(savedOptions.get(2).correct);
    }

    @Test
    void skipsInvalidQuestions() throws Exception {
        runWith(
                entry(1, "Road signs", "   ", "A.", "Yes", "No", null, null),          // no text
                entry(2, "Parking", "Where may you park?", "A.", "Yes", "No", null, null), // unknown category
                entry(3, "Road signs", "Only one option?", "A.", "Yes", null, null, null), // < 2 options
                entry(4, "Road signs", "Answer D is empty?", "D.", "Yes", "No", null, null), // answer missing
                entry(5, "Road signs", "Unreadable answer?", "unknown", "Yes", "No", null, null),
                entry(6, "Road signs", "A valid one?", "A.", "Yes", "No", null, null));

        assertEquals(List.of("A valid one?"), imported().stream().map(q -> q.questionText).toList());
    }

    @Test
    void skipsDuplicatesWithinFileIgnoringCaseAndSpaces() throws Exception {
        runWith(
                entry(1, "Road signs", "What is this?", "A.", "Yes", "No", null, null),
                entry(2, "Road signs", "  what IS this?", "A.", "Yes", "No", null, null));

        assertEquals(1, imported().size());
        assertEquals("Learner's licence question bank PDF, Q1", imported().get(0).source);
    }

    @Test
    void skipsDuplicateOfExistingActiveQuestion() throws Exception {
        Question existing = new Question();
        existing.questionText = "What is this?";
        existing.active = true;
        when(questions.findAll()).thenReturn(List.of(existing));

        runWith(entry(1, "Road signs", "What is this?", "A.", "Yes", "No", null, null));

        assertTrue(imported().isEmpty());
    }

    @Test
    void sameTextWithDifferentImageIsNotADuplicate() throws Exception {
        String withImage = entry(2, "Road signs", "What does this sign mean?", "A.", "Yes", "No", null, null)
                .replace("\"reference_image\": null", "\"reference_image\": \"sign2.png\"");
        runWith(entry(1, "Road signs", "What does this sign mean?", "A.", "Yes", "No", null, null), withImage);

        assertEquals(2, imported().size());
        assertEquals("sign2.png", imported().get(1).imagePath);
    }

    @Test
    void alreadyImportedQuestionsAreNotAddedAgain() throws Exception {
        when(questions.findSourcesStartingWith(QuestionBankImporter.SOURCE_PREFIX))
                .thenReturn(List.of(QuestionBankImporter.SOURCE_PREFIX + "1"));

        runWith(
                entry(1, "Road signs", "Old one?", "A.", "Yes", "No", null, null),
                entry(2, "Road signs", "New one?", "A.", "Yes", "No", null, null));

        assertEquals(List.of("New one?"), imported().stream().map(q -> q.questionText).toList());
    }

    @Test
    void retiresSampleQuestions() throws Exception {
        Question sample = new Question();
        sample.questionId = 99L;
        sample.source = "Sample data";
        sample.active = true;
        when(questions.findBySourceAndActiveTrue("Sample data")).thenReturn(List.of(sample));

        runWith();

        assertFalse(sample.active);
        verify(questions).save(sample);
    }

    @Test
    void missingFileImportsNothingButStillRetiresSamples() throws Exception {
        Question sample = new Question();
        sample.source = "Sample data";
        sample.active = true;
        when(questions.findBySourceAndActiveTrue("Sample data")).thenReturn(List.of(sample));

        importer(List.of(dir.resolve("nope.json").toUri().toString())).run();

        assertFalse(sample.active);
        assertTrue(imported().isEmpty());
        verifyNoInteractions(options);
    }
}
