(() => {
  const intro = document.querySelector("#history-intro");
  const attemptList = document.querySelector("#attempt-list");
  const error = document.querySelector("#history-error");
  const logoutButton = document.querySelector("#logout-button");

  const formatDate = (value) => {
    if (!value) return "Date not available";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "Date not available" : date.toLocaleString();
  };

  const showError = (message) => {
    error.textContent = message;
    error.hidden = false;
  };

  const renderAttempts = (attempts) => {
    attemptList.replaceChildren();
    if (!attempts.length) {
      const empty = document.createElement("p");
      empty.className = "empty-history";
      empty.textContent = "No test attempts yet. Start a practice test to see it here.";
      attemptList.append(empty);
      return;
    }

    attempts.forEach((attempt, index) => {
      const card = document.createElement("article");
      card.className = "attempt-card";

      const details = document.createElement("div");
      const heading = document.createElement("h2");
      heading.textContent = `Practice test ${attempts.length - index}`;
      const date = document.createElement("p");
      date.className = "attempt-date";
      date.textContent = `Started ${formatDate(attempt.startedAt)}`;
      details.append(heading, date);

      const outcome = document.createElement("div");
      outcome.className = "attempt-outcome";
      const score = document.createElement("strong");
      const status = document.createElement("span");
      if (attempt.completed) {
        score.textContent = Number.isFinite(attempt.score) ? `${attempt.score} correct` : "Completed";
        status.textContent = attempt.passed ? "Passed" : "Not passed";
        status.classList.add(attempt.passed ? "is-passed" : "is-failed");
      } else {
        score.textContent = "Incomplete";
        status.textContent = "Not submitted";
      }
      outcome.append(score, status);
      card.append(details, outcome);
      if (attempt.completed) {
        const resultsLink = document.createElement("a");
        resultsLink.className = "attempt-results-link";
        resultsLink.href = `./results.html?attemptId=${encodeURIComponent(attempt.attemptId)}`;
        resultsLink.textContent = "Review results";
        card.append(resultsLink);
      }
      attemptList.append(card);
    });
  };

  logoutButton.addEventListener("click", async () => {
    logoutButton.disabled = true;
    try {
      await window.MockTestApi.logout();
      window.location.href = "./start-test.html";
    } catch (logoutError) {
      console.error("Could not log out.", logoutError);
      showError(logoutError.message);
      logoutButton.disabled = false;
    }
  });

  window.MockTestApi.history()
    .then((attempts) => {
      intro.textContent = "Your completed and unfinished attempts are listed below.";
      renderAttempts(attempts);
    })
    .catch((historyError) => {
      console.error("Could not load test history.", historyError);
      intro.textContent = "Sign in to view your saved test attempts.";
      showError(historyError.message);
    });
})();
