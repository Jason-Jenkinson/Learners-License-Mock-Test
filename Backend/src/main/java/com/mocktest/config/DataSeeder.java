package com.mocktest.config;

import com.mocktest.model.*;
import com.mocktest.repo.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {
    private final CategoryRepository categories;
    private final QuestionRepository questions;
    private final AnswerOptionRepository options;

    public DataSeeder(CategoryRepository c, QuestionRepository q, AnswerOptionRepository o) {
        this.categories = c; this.questions = q; this.options = o;
    }

    @Override
    public void run(String... args) {
        if (categories.count() > 0) return;
        Category rules = cat("Rules of the road");
        Category signs = cat("Road signs");
        Category controls = cat("Controls of the vehicle");

        // Sample questions only - replace with real, sourced questions via the admin API.
        seed(signs, "What does a red octagonal sign mean?", "Sample data", "An octagonal red sign is a stop sign.",
                new String[]{"Stop", "Yield", "No entry"}, 0);
        seed(rules, "What is the general speed limit in an urban area?", "Sample data", "Unless signed otherwise it is 60 km/h.",
                new String[]{"40 km/h", "60 km/h", "80 km/h"}, 1);
        seed(controls, "Which pedal is normally on the far left in a manual car?", "Sample data", "The clutch.",
                new String[]{"Brake", "Accelerator", "Clutch"}, 2);
    }

    private Category cat(String name) {
        Category c = new Category();
        c.categoryName = name;
        return categories.save(c);
    }

    private void seed(Category c, String text, String source, String explanation, String[] opts, int correct) {
        Question q = new Question();
        q.category = c; q.questionText = text; q.source = source; q.explanation = explanation;
        q.verificationStatus = "Verified"; q.active = true;
        q = questions.save(q);
        for (int i = 0; i < opts.length; i++) {
            AnswerOption o = new AnswerOption();
            o.question = q; o.optionText = opts[i]; o.correct = (i == correct);
            options.save(o);
        }
    }
}