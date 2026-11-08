package com.mocktest.config;

import com.mocktest.model.AnswerOption;
import com.mocktest.model.Category;
import com.mocktest.model.Question;
import com.mocktest.repo.AnswerOptionRepository;
import com.mocktest.repo.CategoryRepository;
import com.mocktest.repo.QuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads Assets/extracted_pdf_questions.json into the database on start-up.
 *
 * Each question's source records its number in the PDF ("... Q123"), so the import is
 * idempotent: questions already loaded are skipped and only new ones are added. Questions
 * are imported as Verified (they come from the team's sourced PDF), and the old sample
 * questions are retired so they no longer appear in tests.
 */
@Component
@Order(2)
public class QuestionBankImporter implements CommandLineRunner {
    static final String SOURCE_PREFIX = "Learner's licence question bank PDF, Q";
    private static final Pattern CORRECT = Pattern.compile("^\\s*([A-Da-d])\\s*[.)]");
    private static final Logger log = LoggerFactory.getLogger(QuestionBankImporter.class);

    private final QuestionRepository questions;
    private final AnswerOptionRepository options;
    private final CategoryRepository categories;
    private final ResourceLoader resources;
    private final List<String> locations;

    public QuestionBankImporter(QuestionRepository q, AnswerOptionRepository o, CategoryRepository c,
                                ResourceLoader resources,
                                @Value("${app.question-bank.locations}") List<String> locations) {
        this.questions = q; this.options = o; this.categories = c;
        this.resources = resources; this.locations = locations;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        retireSampleQuestions();

        Resource bank = locations.stream().map(String::trim).map(resources::getResource)
                .filter(Resource::exists).findFirst().orElse(null);
        if (bank == null) {
            log.warn("Question bank not found at {}; no questions imported", locations);
            return;
        }

        JsonNode root;
        try (InputStream in = bank.getInputStream()) {
            root = new ObjectMapper().readTree(in);
        }

        Map<String, Category> byName = new HashMap<>();
        categories.findAll().forEach(c -> byName.put(c.categoryName.toLowerCase(Locale.ROOT), c));
        Set<String> loadedSources = new HashSet<>(questions.findSourcesStartingWith(SOURCE_PREFIX));
        Set<String> seen = new HashSet<>();
        for (Question q : questions.findAll())
            if (q.active) seen.add(dedupeKey(q.questionText, q.imagePath));

        int added = 0, skipped = 0;
        for (JsonNode n : root) {
            int number = n.path("question_number").asInt();
            String source = SOURCE_PREFIX + number;
            if (loadedSources.contains(source)) continue;

            String text = n.path("question_text").asString("").trim();
            String image = n.path("reference_image").isNull() ? null : n.path("reference_image").asString(null);
            Category cat = byName.get(n.path("category").asString("").toLowerCase(Locale.ROOT));
            Matcher m = CORRECT.matcher(n.path("correct_answer").asString(""));
            String correctKey = m.find() ? "answer_text_" + m.group(1).toLowerCase(Locale.ROOT) : null;

            List<String[]> opts = new ArrayList<>(); // {text, isCorrect}
            for (String key : new String[]{"answer_text_a", "answer_text_b", "answer_text_c", "answer_text_d"}) {
                String t = n.path(key).isNull() ? "" : n.path(key).asString("").trim();
                if (!t.isEmpty()) opts.add(new String[]{t, String.valueOf(key.equals(correctKey))});
            }
            long correctCount = opts.stream().filter(o -> o[1].equals("true")).count();

            String problem = text.isEmpty() ? "no question text"
                    : cat == null ? "unknown category '" + n.path("category").asString("") + "'"
                    : opts.size() < 2 ? "fewer than two options"
                    : correctCount != 1 ? "correct answer does not match an option"
                    : !seen.add(dedupeKey(text, image)) ? "duplicate of another question"
                    : null;
            if (problem != null) {
                log.warn("Skipping question {}: {}", number, problem);
                skipped++;
                continue;
            }

            Question q = new Question();
            q.category = cat;
            q.questionText = text;
            q.imagePath = image;
            q.explanation = n.path("answer_information_text").isNull() ? null
                    : n.path("answer_information_text").asString(null);
            q.source = source;
            q.verificationStatus = "Verified";
            q.active = true;
            q = questions.save(q);
            for (String[] o : opts) {
                AnswerOption ao = new AnswerOption();
                ao.question = q;
                ao.optionText = o[0];
                ao.correct = Boolean.parseBoolean(o[1]);
                options.save(ao);
            }
            added++;
        }
        log.info("Question bank import from {}: {} added, {} skipped", bank.getDescription(), added, skipped);
    }

    private void retireSampleQuestions() {
        for (Question q : questions.findBySourceAndActiveTrue("Sample data")) {
            q.active = false;
            questions.save(q);
        }
    }

    private static String dedupeKey(String text, String image) {
        return text.trim().toLowerCase(Locale.ROOT) + "|" + (image == null ? "" : image);
    }
}
