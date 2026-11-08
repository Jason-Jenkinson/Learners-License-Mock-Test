package com.mocktest.config;

import com.mocktest.model.Category;
import com.mocktest.repo.CategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Creates the three CLLT categories on first start. Questions are loaded by {@link QuestionBankImporter}. */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {
    public static final String RULES = "Rules of the road";
    public static final String SIGNS = "Road signs";
    public static final String CONTROLS = "Controls of the vehicle";

    private final CategoryRepository categories;

    public DataSeeder(CategoryRepository c) {
        this.categories = c;
    }

    @Override
    public void run(String... args) {
        for (String name : new String[]{RULES, SIGNS, CONTROLS}) {
            if (categories.findByCategoryName(name).isEmpty()) {
                Category c = new Category();
                c.categoryName = name;
                categories.save(c);
            }
        }
    }
}
