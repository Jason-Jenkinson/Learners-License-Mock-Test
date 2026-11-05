(() => {
  const QUESTION_COUNT = 64;
  const TEST_DURATION_MS = 20 * 60 * 1000;
  const STORAGE_KEY = "learners-license-practice-test";
  const startButton = document.querySelector("#start-button");
  const status = document.querySelector("#setup-status");
  const accountNav = document.querySelector("#account-nav");

  const showError = (message) => {
    status.textContent = message;
    status.classList.add("is-error");
  };

  const renderAccountNav = (user) => {
    accountNav.replaceChildren();
    if (user && !user.guest) {
      const greeting = document.createElement("span");
      greeting.textContent = `Signed in as ${user.username}`;
      const historyLink = document.createElement("a");
      historyLink.href = "./history.html";
      historyLink.textContent = "Test history";
      const logoutButton = document.createElement("button");
      logoutButton.type = "button";
      logoutButton.textContent = "Log out";
      logoutButton.addEventListener("click", async () => {
        logoutButton.disabled = true;
        try {
          await window.MockTestApi.logout();
          window.location.reload();
        } catch (error) {
          console.error("Could not log out.", error);
          showError(error.message);
          logoutButton.disabled = false;
        }
      });
      accountNav.append(greeting, historyLink, logoutButton);
      return;
    }

    const loginLink = document.createElement("a");
    loginLink.href = "./login.html";
    loginLink.textContent = "Log in";
    const registerLink = document.createElement("a");
    registerLink.href = "./register.html";
    registerLink.textContent = "Create account";
    accountNav.append(loginLink, registerLink);
  };

  window.MockTestApi.currentUser()
    .then(renderAccountNav)
    .catch((error) => {
      console.error("Could not check the current account.", error);
      renderAccountNav({ guest: true });
      status.textContent = "Start a guest test or sign in; the backend must be running.";
      status.classList.add("is-error");
    });

  startButton.addEventListener("click", async () => {
    startButton.disabled = true;
    status.classList.remove("is-error");
    status.textContent = "Preparing your test…";

    try {
      const view = await window.MockTestApi.startTest();
      if (!view || !Number.isFinite(view.attemptId) || !Array.isArray(view.questions) || !view.questions.length) {
        throw new Error("The server did not return any questions for this test.");
      }

      const now = Date.now();
      const test = {
        attemptId: view.attemptId,
        questions: view.questions,
        answers: {},
        startedAt: now,
        expiresAt: now + TEST_DURATION_MS,
        completed: false,
      };

      try {
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(test));
      } catch {
        throw new Error("Browser session storage is unavailable. Enable it to start the test.");
      }
      window.location.href = "./test-question.html";
    } catch (error) {
      console.error("Could not start the practice test.", error);
      showError(error instanceof Error ? error.message : "An unexpected error stopped the test from starting.");
      startButton.disabled = false;
    }
  });
})();
