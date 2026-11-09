/* OIEP — minimal progressive enhancement.
   The form and search work without this file. */
(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

  /* ---------------------------------------------------------
     Debrief form
     --------------------------------------------------------- */
  const form = $("#debrief-form");

  if (form) {
    const messagesEl = $("#form-messages");
    const statusEl = $("#form-status");
    const submitButton = $("[data-submit]", form);
    const submitLabel = submitButton ? $(".btn__label", submitButton) : null;
    const storyInput = $("#story");
    const storyCount = $("[data-count]", form);
    const storyCountWrap = $("#story-count", form);
    const extras = $(".extras", form);

    const msg = (key) => (messagesEl && messagesEl.dataset[key]) || "";

    const fieldNames = ["company", "role", "round", "outcome", "story", "confirm"];

    const errorElement = (name) => document.getElementById(`${name}-error`);

    const groupInputs = (name) =>
      $$(`input[name="${name}"]`, form).filter((input) => input.type !== "hidden");

    const isFieldValid = (name) => {
      if (name === "round" || name === "outcome") {
        return groupInputs(name).some((input) => input.checked);
      }

      if (name === "confirm") {
        const confirm = $("#confirm", form);
        return Boolean(confirm && confirm.checked);
      }

      if (name === "story") {
        if (!storyInput) return true;
        const length = storyInput.value.trim().length;
        return length >= 15 && length <= 4000;
      }

      const input = form.elements.namedItem(name);
      if (!input || typeof input.value !== "string") return true;
      return input.value.trim().length > 0;
    };

    const validationMessage = (name) => {
      if (name === "story") {
        if (!storyInput) return "";
        const length = storyInput.value.trim().length;
        if (length === 0) return msg("msgStory");
        return msg("msgStorySize");
      }

      const map = {
        company: "msgCompany",
        role: "msgRole",
        round: "msgRound",
        outcome: "msgOutcome",
        confirm: "msgConsent",
      };

      return msg(map[name]);
    };

    const singleFields = new Set(["company", "role", "story", "confirm", "location", "salary", "heardBack"]);

    const setAriaInvalid = (name, invalid) => {
      const inputs = singleFields.has(name)
        ? [form.elements.namedItem(name)].filter(Boolean)
        : groupInputs(name);

      inputs.forEach((input) => {
        if (!input || !input.setAttribute) return;
        if (invalid) {
          input.setAttribute("aria-invalid", "true");
          if (!input.getAttribute("aria-describedby")) {
            input.setAttribute("aria-describedby", `${name}-error`);
          }
        } else {
          input.removeAttribute("aria-invalid");
        }
      });
    };

    const showError = (name, text) => {
      const error = errorElement(name);
      setAriaInvalid(name, true);

      if (error) {
        error.textContent = text;
        error.setAttribute("role", "alert");
      }
    };

    const clearError = (name) => {
      const error = errorElement(name);
      setAriaInvalid(name, false);

      if (error) {
        error.textContent = "";
        error.removeAttribute("role");
      }
    };

    const validateField = (name, show) => {
      const valid = isFieldValid(name);
      if (!valid && show) {
        showError(name, validationMessage(name));
      } else if (valid) {
        clearError(name);
      }
      return valid;
    };

    const firstInvalidField = () => {
      for (const name of fieldNames) {
        if (!isFieldValid(name)) return name;
      }
      return null;
    };

    const focusField = (name) => {
      if (!name) return;
      const input = form.elements.namedItem(name);
      if (input && input.focus) {
        input.focus({ preventScroll: false });
      } else {
        const group = groupInputs(name)[0];
        if (group) group.focus({ preventScroll: false });
      }
    };

    const refreshStoryCount = () => {
      if (!storyInput || !storyCount) return;
      const count = storyInput.value.length;
      storyCount.textContent = String(count);
      if (storyCountWrap) storyCountWrap.hidden = count === 0;
    };

    // Keep native validation as the no-JS fallback; this flag is only set when JS runs.
    form.noValidate = true;

    // Mark any server-rendered errors as invalid and expose the extras panel they live in.
    $$(".field-error", form).forEach((error) => {
      if (error.id && error.textContent.trim()) {
        const name = error.id.replace(/-error$/, "");
        setAriaInvalid(name, true);
        if (extras && extras.contains(error)) extras.open = true;
      }
    });

    const serverAlert = $(".form-alert");
    if (serverAlert) {
      window.setTimeout(() => serverAlert.focus({ preventScroll: false }), 30);
    }

    if (storyInput) {
      refreshStoryCount();
      storyInput.addEventListener("input", () => {
        refreshStoryCount();
        if (storyInput.getAttribute("aria-invalid") === "true") validateField("story", true);
      });
    }

    ["company", "role"].forEach((name) => {
      const input = form.elements.namedItem(name);
      if (!input) return;
      input.addEventListener("blur", () => {
        if (input.value.trim().length > 0) validateField(name, false);
      });
      input.addEventListener("input", () => {
        if (input.getAttribute("aria-invalid") === "true" && input.value.trim().length > 0) {
          clearError(name);
        }
      });
    });

    ["round", "outcome"].forEach((name) => {
      groupInputs(name).forEach((input) => {
        input.addEventListener("change", () => clearError(name));
      });
    });

    const confirm = $("#confirm", form);
    if (confirm) {
      confirm.addEventListener("change", () => {
        if (confirm.checked) clearError("confirm");
      });
    }

    // Auto-close/clear optional server errors when the user revisits the field.
    ["location", "salary"].forEach((name) => {
      const input = form.elements.namedItem(name);
      if (!input) return;
      input.addEventListener("input", () => {
        if (input.getAttribute("aria-invalid") === "true") clearError(name);
      });
    });

    form.addEventListener("submit", (event) => {
      const invalid = firstInvalidField();

      if (invalid) {
        event.preventDefault();
        fieldNames.forEach((name) => validateField(name, true));
        if (statusEl) {
          statusEl.textContent = msg("msgSummary");
        }
        focusField(invalid);
        return;
      }

      if (submitButton) {
        submitButton.disabled = true;
        submitButton.setAttribute("aria-busy", "true");
        if (submitLabel) submitLabel.textContent = msg("msgSubmitting");
      }
      form.setAttribute("aria-busy", "true");
      // Calling native submit bypasses this handler on the second pass.
      form.submit();
    });
  }

  /* ---------------------------------------------------------
     Browse filters — auto apply, with an Apply fallback
     --------------------------------------------------------- */
  $$("[data-auto-submit]").forEach((control) => {
    control.addEventListener("change", () => {
      const searchForm = control.closest("form");
      if (!searchForm) return;
      if (typeof searchForm.requestSubmit === "function") {
        searchForm.requestSubmit();
      } else {
        searchForm.submit();
      }
    });
  });
})();
