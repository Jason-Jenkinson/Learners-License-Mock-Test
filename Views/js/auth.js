(() => {
  const form = document.querySelector("#login-form, #register-form");
  const error = document.querySelector("#form-error");
  const submitButton = form.querySelector('button[type="submit"]');
  const isRegister = form.id === "register-form";

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    error.hidden = true;
    error.textContent = "";

    const formData = new FormData(form);
    const username = String(formData.get("username") || "").trim();
    const password = String(formData.get("password") || "");
    if (username.length < 3) {
      error.textContent = "Username must be at least 3 characters.";
      error.hidden = false;
      return;
    }
    if (password.length < 6) {
      error.textContent = "Password must be at least 6 characters.";
      error.hidden = false;
      return;
    }
    if (isRegister && password !== formData.get("confirm-password")) {
      error.textContent = "The passwords do not match.";
      error.hidden = false;
      return;
    }

    submitButton.disabled = true;
    try {
      const credentials = { username, password };
      if (isRegister) {
        await window.MockTestApi.register(credentials);
      } else {
        await window.MockTestApi.login(credentials);
      }
      const claimAttempt = new URLSearchParams(window.location.search).get("claimAttempt");
      if (claimAttempt) {
        const attemptId = Number(claimAttempt);
        if (!Number.isSafeInteger(attemptId) || attemptId < 1) {
          throw new Error("The result to save has an invalid attempt ID.");
        }
        await window.MockTestApi.claimAttempt(attemptId);
      }
      window.location.href = claimAttempt ? "./results.html" : "./start-test.html";
    } catch (requestError) {
      console.error("Could not complete authentication or save the guest result.", requestError);
      error.textContent = requestError.message;
      error.hidden = false;
      submitButton.disabled = false;
    }
  });
})();
