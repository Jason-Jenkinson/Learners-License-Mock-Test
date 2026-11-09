(() => {
  const isHttp = window.location.protocol === "http:" || window.location.protocol === "https:";
  const API_BASE = isHttp
    ? `${window.location.protocol}//${window.location.hostname}:8080/api`
    : null;

  const request = async (path, options = {}) => {
    if (!API_BASE) {
      throw new Error("Open this page through VS Code Live Server or another HTTP server.");
    }

    const response = await fetch(`${API_BASE}${path}`, {
      ...options,
      credentials: "include",
      headers: {
        ...(options.body ? { "Content-Type": "application/json" } : {}),
        ...options.headers,
      },
    });
    const responseText = await response.text();
    let payload = null;
    if (responseText) {
      try {
        payload = JSON.parse(responseText);
      } catch {
        payload = responseText;
      }
    }

    if (!response.ok) {
      const message =
        payload && typeof payload === "object"
          ? payload.detail || payload.message || payload.error
          : payload;
      throw new Error(message || `Request failed with status ${response.status}.`);
    }

    return payload;
  };

  const jsonRequest = (method, body) => ({
    method,
    body: JSON.stringify(body),
  });

  window.MockTestApi = Object.freeze({
    currentUser: () => request("/auth/me"),
    login: (credentials) => request("/auth/login", jsonRequest("POST", credentials)),
    register: (credentials) => request("/auth/register", jsonRequest("POST", credentials)),
    logout: () => request("/auth/logout", jsonRequest("POST", {})),
    startTest: () => request("/tests", jsonRequest("POST", {})),
    saveAnswer: (attemptId, answer) =>
      request(`/tests/${encodeURIComponent(attemptId)}/answers`, jsonRequest("PUT", answer)),
    submitTest: (attemptId) =>
      request(`/tests/${encodeURIComponent(attemptId)}/submit`, jsonRequest("POST", {})),
    claimAttempt: (attemptId) =>
      request(`/tests/${encodeURIComponent(attemptId)}/claim`, jsonRequest("POST", {})),
    getResults: (attemptId) => request(`/tests/${encodeURIComponent(attemptId)}/results`),
    history: () => request("/history"),
  });
})();
