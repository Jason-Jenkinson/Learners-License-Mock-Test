(() => {
  const STORAGE_KEY = "learners-license-practice-test";
  const score = document.querySelector("#score");
  const passStatus = document.querySelector("#pass-status");
  const percentage = document.querySelector("#percentage");
  const questionTotal = document.querySelector("#question-total");
  const completionTime = document.querySelector("#completion-time");
  const intro = document.querySelector("#results-intro");
  const categoryList = document.querySelector("#category-list");
  const reviewCount = document.querySelector("#review-count");
  const reviewList = document.querySelector("#review-list");
  const categoryFilter = document.querySelector("#category-filter");
  const error = document.querySelector("#results-error");
  const guestSaveActions = document.querySelector("#guest-save-actions");
  const historyLink = document.querySelector("#history-link");
  const saveLoginLink = document.querySelector("#save-login-link");
  const saveRegisterLink = document.querySelector("#save-register-link");
  let test;
  let incorrectQuestions = [];

  const formatElapsedTime = (milliseconds) => {
    const totalSeconds = Math.floor(Math.max(0, milliseconds) / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${String(seconds).padStart(2, "0")}`;
  };

  const createCategoryCard = (category) => {
    const card = document.createElement("article");
    card.className = "category-card";

    const name = document.createElement("h3");
    name.textContent = category.category;
    const result = document.createElement("p");
    result.className = "category-score";
    result.textContent = `${category.correct}/${category.asked} correct`;

    const requirement = document.createElement("p");
    requirement.className = "category-requirement";
    requirement.textContent = `Required to pass: ${category.required}/${category.asked}`;

    const status = document.createElement("span");
    status.className = `category-status${category.passed ? " is-passed" : " is-failed"}`;
    status.textContent = category.passed ? "Passed" : "Needs practice";

    card.append(name, result, requirement, status);
    categoryList.append(card);

    const option = document.createElement("option");
    option.value = category.category;
    option.textContent = category.category;
    categoryFilter.append(option);
  };

  const addAnswerLine = (card, label, value) => {
    const line = document.createElement("p");
    line.className = "review-answer";
    const title = document.createElement("strong");
    title.textContent = `${label}: `;
    line.append(title, document.createTextNode(value || "Not answered"));
    card.append(line);
  };

  const renderReview = () => {
    const selectedCategory = categoryFilter.value;
    const items = selectedCategory === "all"
      ? incorrectQuestions
      : incorrectQuestions.filter((item) => item.category === selectedCategory);

    reviewList.replaceChildren();
    reviewCount.textContent = `${items.length} incorrect or unanswered question${items.length === 1 ? "" : "s"}`;
    if (!items.length) {
      const empty = document.createElement("p");
      empty.className = "empty-review";
      empty.textContent = incorrectQuestions.length
        ? "No incorrect answers in this category."
        : "Great work — all your answers are correct.";
      reviewList.append(empty);
      return;
    }

    items.forEach((item) => {
      const card = document.createElement("article");
      card.className = "review-card";

      const status = document.createElement("p");
      status.className = `review-status${item.selectedOptionId === null ? " is-unanswered" : ""}`;
      status.textContent = item.selectedOptionId === null ? "Unanswered" : "Incorrect";

      const heading = document.createElement("h3");
      const question = test.questions.find((entry) => entry.questionId === item.questionId);
      const order = item.order || (question ? question.order : "");
      heading.textContent = `${order ? `Question ${order}: ` : ""}${item.text}`;

      const category = document.createElement("p");
      category.className = "review-category";
      category.textContent = item.category;

      card.append(status, category, heading);
      addAnswerLine(card, "Your answer", item.selectedText);
      addAnswerLine(card, "Correct answer", item.correctText);

      if (item.explanation) {
        const explanation = document.createElement("p");
        explanation.className = "review-explanation";
        explanation.textContent = item.explanation;
        card.append(explanation);
      }

      reviewList.append(card);
    });
  };

  const renderResult = (result) => {
    if (!result || !Array.isArray(result.categories) || !Array.isArray(result.incorrect)) {
      throw new Error("The server returned an incomplete result.");
    }

    const total = Number(result.total);
    const correct = Number(result.score);
    if (!Number.isFinite(total) || total < 1 || !Number.isFinite(correct)) {
      throw new Error("The server returned an invalid score.");
    }

    score.textContent = `${correct}/${total}`;
    passStatus.textContent = result.passed ? "Passed" : "Not passed";
    passStatus.classList.toggle("is-passed", Boolean(result.passed));
    passStatus.classList.toggle("is-failed", !result.passed);
    percentage.textContent = `${Math.round((correct / total) * 100)}%`;
    questionTotal.textContent = String(total);

    const finishedAt = test.finishedAt || Date.now();
    const elapsed = Number.isFinite(test.startedAt)
      ? Math.min(20 * 60 * 1000, finishedAt - test.startedAt)
      : null;
    completionTime.textContent = elapsed === null ? "—" : formatElapsedTime(elapsed);
    intro.textContent = test.timeExpired
      ? "Time ran out. Your submitted score and category results are below."
      : "Your submitted score and category results are below.";

    categoryList.replaceChildren();
    categoryFilter.replaceChildren(new Option("All categories", "all"));
    result.categories.forEach(createCategoryCard);
    incorrectQuestions = result.incorrect;
    renderReview();
  };

  const updateAccountActions = () => {
    window.MockTestApi.currentUser()
      .then((user) => {
        if (user.guest) {
          const claimQuery = `?claimAttempt=${encodeURIComponent(test.attemptId)}`;
          saveLoginLink.href = `./login.html${claimQuery}`;
          saveRegisterLink.href = `./register.html${claimQuery}`;
          guestSaveActions.hidden = false;
        } else {
          historyLink.hidden = false;
        }
      })
      .catch((accountError) => {
        console.error("Could not check the account for result actions.", accountError);
      });
  };

  categoryFilter.addEventListener("change", renderReview);

  const loadStoredTest = () => {
    const stored = sessionStorage.getItem(STORAGE_KEY);
    const parsed = stored ? JSON.parse(stored) : null;
    return parsed && Number.isFinite(parsed.attemptId) && Array.isArray(parsed.questions)
      ? parsed
      : null;
  };

  try {
    const requestedAttemptId = new URLSearchParams(window.location.search).get("attemptId");
    const storedTest = loadStoredTest();
    if (requestedAttemptId) {
      const attemptId = Number(requestedAttemptId);
      if (!Number.isSafeInteger(attemptId) || attemptId < 1) {
        throw new Error("The requested test attempt is invalid.");
      }
      test = storedTest && storedTest.attemptId === attemptId
        ? storedTest
        : { attemptId, questions: [], startedAt: NaN };
    } else {
      test = storedTest;
      if (!test) throw new Error("No submitted practice test was found in this browser session.");
    }

    updateAccountActions();
    if (test.result && !requestedAttemptId) {
      renderResult(test.result);
    } else {
      const historyRequest = requestedAttemptId
        ? window.MockTestApi.history()
        : Promise.resolve([]);
      Promise.all([window.MockTestApi.getResults(test.attemptId), historyRequest])
        .then(([result, attempts]) => {
          if (requestedAttemptId) {
            const attempt = attempts.find((entry) => entry.attemptId === test.attemptId);
            if (!attempt) throw new Error("This attempt was not found in your account history.");
            test.startedAt = Date.parse(attempt.startedAt);
            test.finishedAt = Date.parse(attempt.submittedAt);
          }
          renderResult(result);
        })
        .catch((resultError) => {
          console.error("Could not load practice test results.", resultError);
          error.hidden = false;
          error.textContent = `Could not load the submitted result. ${resultError.message}`;
          document.querySelector(".score-card").hidden = true;
          document.querySelector(".category-section").hidden = true;
          document.querySelector(".review-section").hidden = true;
        });
    }
  } catch (resultError) {
    console.error("Could not load practice test results.", resultError);
    error.hidden = false;
    error.textContent = "No submitted test is available. Start a new practice test to see your results.";
    document.querySelector(".score-card").hidden = true;
    document.querySelector(".category-section").hidden = true;
    document.querySelector(".review-section").hidden = true;
  }
})();
