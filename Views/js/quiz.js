(() => {
  const STORAGE_KEY = "learners-license-practice-test";
  const questionCount = document.querySelector("#question-count");
  const answeredCount = document.querySelector("#answered-count");
  const progress = document.querySelector("#quiz-progress");
  const questionNumber = document.querySelector("#question-number");
  const questionText = document.querySelector("#question-text");
  const questionImage = document.querySelector("#question-image");
  const answerOptions = document.querySelector("#answer-options");
  const timer = document.querySelector("#timer");
  const timerContainer = document.querySelector(".timer");
  const previousButton = document.querySelector("#previous-button");
  const nextButton = document.querySelector("#next-button");
  const finishButton = document.querySelector("#finish-button");
  const exitLink = document.querySelector("#exit-link");
  const error = document.querySelector("#quiz-error");
  let test;
  let currentIndex = 0;
  let timerInterval;
  let isSaving = false;
  let isSubmitting = false;
  let autoSubmitAttempted = false;

  const showError = (message) => {
    error.hidden = false;
    error.textContent = message;
  };

  const clearError = () => {
    error.hidden = true;
    error.textContent = "";
  };

  const loadTest = () => {
    try {
      const stored = sessionStorage.getItem(STORAGE_KEY);
      if (!stored) return null;
      const parsed = JSON.parse(stored);
      if (
        !Number.isFinite(parsed.attemptId) ||
        !Array.isArray(parsed.questions) ||
        parsed.questions.length === 0 ||
        !parsed.answers ||
        !Number.isFinite(parsed.expiresAt)
      ) {
        return null;
      }
      return parsed;
    } catch (loadError) {
      console.error("Could not read the saved practice test.", loadError);
      return null;
    }
  };

  const saveTest = () => {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(test));
      return true;
    } catch (saveError) {
      console.error("Could not save practice test progress.", saveError);
      showError("Your progress could not be saved in this browser session.");
      return false;
    }
  };

  const getAnsweredCount = () => Object.keys(test.answers).length;

  const updateProgress = () => {
    const total = test.questions.length;
    questionCount.textContent = `Question ${currentIndex + 1} of ${total}`;
    answeredCount.textContent = `${getAnsweredCount()} of ${total} answered`;
    progress.max = total;
    progress.value = currentIndex + 1;
  };

  const setBusy = (busy) => {
    isSaving = busy;
    answerOptions.querySelectorAll("input").forEach((input) => {
      input.disabled = busy;
    });
    previousButton.disabled = busy || currentIndex === 0;
    nextButton.disabled = busy;
    finishButton.disabled = busy || isSubmitting;
  };

  const imageUrl = (imagePath) => {
    const normalized = imagePath.replace(/\\/g, "/").replace(/^\.?\//, "");
    const assetPath = normalized.startsWith("Assets/")
      ? normalized.slice("Assets/".length)
      : normalized;
    return `../Assets/${assetPath.split("/").map(encodeURIComponent).join("/")}`;
  };

  const renderQuestion = (moveFocus = false) => {
    const question = test.questions[currentIndex];
    const questionId = String(question.questionId);
    questionNumber.textContent = `Question ${currentIndex + 1}`;
    questionText.textContent = question.text;

    if (question.imagePath) {
      questionImage.src = imageUrl(question.imagePath);
      questionImage.alt = "Illustration for this question";
      questionImage.hidden = false;
      questionImage.onerror = () => {
        questionImage.hidden = true;
      };
    } else {
      questionImage.removeAttribute("src");
      questionImage.hidden = true;
    }

    answerOptions.replaceChildren();
    question.options.forEach((option) => {
      const label = document.createElement("label");
      label.className = "answer-option";

      const input = document.createElement("input");
      input.type = "radio";
      input.name = "answer";
      input.value = String(option.optionId);
      input.checked = String(test.answers[questionId]) === String(option.optionId);

      const text = document.createElement("span");
      text.textContent = option.text;
      label.append(input, text);
      answerOptions.append(label);
    });

    answerOptions.querySelectorAll('input[name="answer"]').forEach((input) => {
      input.addEventListener("change", async () => {
        const previousOptionId = test.answers[questionId];
        const optionId = Number(input.value);
        test.answers[questionId] = optionId;
        updateProgress();
        clearError();
        setBusy(true);

        try {
          await window.MockTestApi.saveAnswer(test.attemptId, {
            questionId: question.questionId,
            optionId,
          });
          saveTest();
        } catch (saveError) {
          console.error("Could not save the selected answer.", saveError);
          if (previousOptionId === undefined) {
            delete test.answers[questionId];
          } else {
            test.answers[questionId] = previousOptionId;
          }
          renderQuestion();
          showError(`Your answer was not saved. ${saveError.message}`);
        } finally {
          setBusy(false);
          updateProgress();
        }
      });
    });

    previousButton.disabled = isSaving || currentIndex === 0;
    nextButton.disabled = isSaving;
    nextButton.hidden = currentIndex === test.questions.length - 1;
    finishButton.hidden = false;
    updateProgress();
    if (moveFocus) questionText.focus();
  };

  const formatTime = (milliseconds) => {
    const seconds = Math.max(0, Math.ceil(milliseconds / 1000));
    const minutesPart = Math.floor(seconds / 60);
    const secondsPart = seconds % 60;
    return `${String(minutesPart).padStart(2, "0")}:${String(secondsPart).padStart(2, "0")}`;
  };

  const submitTest = async (timeExpired = false) => {
    if (isSubmitting || isSaving) return;
    const unanswered = test.questions.length - getAnsweredCount();
    if (!timeExpired && unanswered > 0) {
      const shouldSubmit = window.confirm(
        `You have ${unanswered} unanswered question${unanswered === 1 ? "" : "s"}. Finish the test anyway?`,
      );
      if (!shouldSubmit) return;
    } else if (!timeExpired && !window.confirm("Finish and submit your test?")) {
      return;
    }

    isSubmitting = true;
    window.clearInterval(timerInterval);
    finishButton.disabled = true;
    finishButton.textContent = "Submitting…";
    nextButton.disabled = true;
    previousButton.disabled = true;
    clearError();

    try {
      const result = await window.MockTestApi.submitTest(test.attemptId);
      test.completed = true;
      test.timeExpired = timeExpired;
      test.finishedAt = Date.now();
      test.result = result;
      saveTest();
      window.location.href = "./results.html";
    } catch (submitError) {
      console.error("Could not submit the practice test.", submitError);
      showError(`The test was not submitted. Check your connection and try again. ${submitError.message}`);
      isSubmitting = false;
      finishButton.disabled = false;
      finishButton.textContent = Date.now() >= test.expiresAt ? "Retry submit" : "Finish test";
      nextButton.disabled = false;
      previousButton.disabled = currentIndex === 0;
      timerInterval = window.setInterval(updateTimer, 1000);
    }
  };

  const updateTimer = () => {
    const remaining = test.expiresAt - Date.now();
    timer.textContent = formatTime(remaining);
    timerContainer.classList.toggle("is-low", remaining <= 60_000);
    if (remaining <= 0 && !autoSubmitAttempted && !isSaving && !isSubmitting) {
      autoSubmitAttempted = true;
      submitTest(true);
    }
  };

  test = loadTest();
  if (!test || test.completed) {
    questionText.textContent = "There is no active test to continue.";
    questionCount.textContent = "";
    answeredCount.textContent = "";
    progress.hidden = true;
    document.querySelector(".quiz-status").hidden = true;
    showError("Start a new practice test to load a fresh set of questions.");
    previousButton.hidden = true;
    nextButton.hidden = true;
    finishButton.hidden = true;
    return;
  }

  currentIndex = Number.isInteger(test.currentIndex)
    ? Math.max(0, Math.min(test.currentIndex, test.questions.length - 1))
    : 0;

  previousButton.addEventListener("click", () => {
    if (currentIndex > 0 && !isSaving) {
      currentIndex -= 1;
      test.currentIndex = currentIndex;
      saveTest();
      renderQuestion(true);
    }
  });

  nextButton.addEventListener("click", () => {
    if (currentIndex < test.questions.length - 1 && !isSaving) {
      currentIndex += 1;
      test.currentIndex = currentIndex;
      saveTest();
      renderQuestion(true);
    }
  });

  finishButton.addEventListener("click", () => submitTest(Date.now() >= test.expiresAt));
  exitLink.addEventListener("click", (event) => {
    if (!window.confirm("Leave this test? Your saved answers will remain, but the test will not be submitted.")) {
      event.preventDefault();
    }
  });

  renderQuestion();
  updateTimer();
  timerInterval = window.setInterval(updateTimer, 1000);
})();
