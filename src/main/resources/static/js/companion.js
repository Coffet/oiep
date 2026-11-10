/* OIEP — optional AI companion: user-helper bridge (progressive enhancement).
 *
 * The iframe (served by the separate oiep-backendAI process) works on its own: it runs the
 * chat, reviews the draft and lets the visitor copy it. This file adds two conveniences:
 *  - it turns the floating launcher into an open/close toggle for the embedded panel
 *    (without this script the launcher is a plain link to the standalone widget), and
 *  - it copies a reviewed draft into the OIEP form.
 *
 * Safety rules, deliberately strict:
 *  - messages are accepted only from the exact configured widget origin AND from the
 *    embedded iframe's window (event.origin + event.source);
 *  - the OIEP form is never submitted, and a text field is never overwritten: only empty
 *    fields are filled (radio groups are only set when unanswered; checkboxes are only
 *    added, never removed);
 *  - every user-facing string comes from the messages-companion bundle via data-* attributes
 *    rendered by the fragment — this file contains no copy;
 *  - a status region always tells the visitor to review the form before submitting.
 *
 * The companion payload is InterviewExperience v1 (oiep-backendAI contracts/). The mapping to
 * OIEP controls is isolated in the single block marked below: that is the one place to adapt
 * when the OIEP form fields change.
 */
(() => {
  "use strict";

  const root = document.querySelector("[data-companion-helper]");
  if (!root) return;

  const form = document.querySelector("#debrief-form");
  const iframe = root.querySelector("[data-companion-widget]");
  const applyButton = root.querySelector("[data-companion-apply]");
  const statusEl = root.querySelector("[data-companion-status]");
  const fallbackEl = root.querySelector("[data-companion-fallback]");

  if (!form || !iframe || !applyButton || !statusEl) return;

  /* ------------------------------------------------------------------
     Exact widget origin. Rendered by the server-side condition check, so
     it is always a plain http(s) origin. Without a usable origin the
     script stays inert and the iframe keeps working on its own.
     ------------------------------------------------------------------ */
  const configuredOrigin = (root.getAttribute("data-widget-origin") || "").trim();
  let widgetOrigin = "";
  try {
    if (configuredOrigin) widgetOrigin = new URL(configuredOrigin).origin;
  } catch (ignored) {
    widgetOrigin = "";
  }
  if (!widgetOrigin) return;

  const REQUEST_TIMEOUT_MS = 12000;

  const message = (name) => (root.getAttribute("data-msg-" + name) || "").trim();

  /* All strings are server-rendered from messages-companion (EN / zh-HK). */
  const MESSAGE = {
    checking: message("checking"),
    applied: message("applied"),
    appliedCount: message("applied-count"),
    unchanged: message("unchanged"),
    noMatch: message("no-match"),
    skipped: message("skipped"),
    notReady: message("not-ready"),
    unavailable: message("unavailable")
  };

  let pendingRequestId = null;
  let pendingTimer = null;
  let lastAppliedSignature = "";
  let lastAppliedResult = null;

  const setStatus = (text) => {
    if (typeof text === "string" && text) statusEl.textContent = text;
  };

  const showFallback = () => {
    if (fallbackEl) fallbackEl.hidden = false;
  };

  const hideFallback = () => {
    if (fallbackEl) fallbackEl.hidden = true;
  };

  const formatCount = (template, count) =>
    template ? template.replace("{0}", String(count)) : "";

  const joinText = (parts) => parts.filter((part) => part && part.length > 0).join(" ");

  const dispatch = (control, type) => {
    control.dispatchEvent(new Event(type, { bubbles: true }));
  };

  /* =====================================================================
     PREFILL MAPPING — the only place to adapt when the OIEP form changes.
     ---------------------------------------------------------------------
     Source shape (InterviewExperience v1 from oiep-backendAI):
       company, role_title, location, overall_experience,
       work_mode            ONSITE | HYBRID | REMOTE | UNKNOWN
       outcome              OFFER | REJECTED | WITHDRAWN | PENDING | NO_RESPONSE
       rounds[].index       contiguous from 1
       rounds[].type        SCREEN | PHONE | TECHNICAL | SYSTEM_DESIGN |
                            BEHAVIORAL | TAKE_HOME | ONSITE | HR | OTHER
       rounds[].topics      free-text strings (max 8)
     Target (this repository's index.jsp debrief form):
       #company, #role, #location, #story,
       input[name=workMode], input[name=outcome], input[name=round],
       input[name=questionTypes]
     UNKNOWN/unsupported values are skipped, never guessed.
     ===================================================================== */
  const PREFILL_MAP = {
    text: [
      { source: "company", target: "#company" },
      { source: "role_title", target: "#role" },
      { source: "location", target: "#location" },
      { source: "overall_experience", target: "#story" }
    ],
    radios: [
      {
        source: "work_mode",
        name: "workMode",
        values: { ONSITE: "onsite", HYBRID: "hybrid", REMOTE: "remote" }
      },
      {
        source: "outcome",
        name: "outcome",
        values: {
          OFFER: "offer",
          REJECTED: "rejection",
          WITHDRAWN: "withdrew",
          PENDING: "waiting",
          NO_RESPONSE: "ghosted"
        }
      },
      {
        fromFirstRound: true,
        name: "round",
        values: {
          SCREEN: "phone",
          PHONE: "phone",
          TECHNICAL: "technical",
          TAKE_HOME: "takehome",
          SYSTEM_DESIGN: "systemdesign",
          BEHAVIORAL: "behavioral",
          ONSITE: "other",
          HR: "other",
          OTHER: "other"
        }
      }
    ],
    checkboxGroup: "questionTypes",
    /* Free-text round topics mapped to the OIEP questionTypes checkboxes. */
    topicKeywords: {
      algorithm: [
        "algorithm", "algorithms", "data structure", "data structures", "coding",
        "leetcode", "dynamic programming", "graph", "graphs", "tree", "trees",
        "sorting", "complexity", "big o"
      ],
      systemdesign: [
        "system design", "systemdesign", "architecture", "scalability",
        "distributed", "microservice", "microservices", "api design"
      ],
      takehome: [
        "take home", "take-home", "takehome", "homework", "assignment",
        "coding exercise", "side project"
      ],
      behavioral: [
        "behavioral", "behavioural", "behaviour", "teamwork", "conflict",
        "collaboration", "leadership", "strengths", "weaknesses", "motivation"
      ],
      domain: [
        "domain", "java", "python", "javascript", "typescript", "sql", "database",
        "backend", "frontend", "react", "spring", "kubernetes", "docker", "cloud",
        "devops", "networking", "security", "ios", "android", "mobile"
      ],
      culture: [
        "culture", "values", "company values", "why this company", "why do you want",
        "team fit", "mission"
      ],
      casestudy: [
        "case study", "case-study", "casestudy", "business case", "product sense",
        "product design", "estimation", "guesstimate"
      ]
    }
  };
  /* ================== END PREFILL MAPPING ============================== */

  const groupInputs = (name) =>
    Array.from(form.querySelectorAll('input[type="radio"][name="' + name + '"]'));

  const isUsableControl = (control) =>
    Boolean(control) && !control.disabled && !control.readOnly && control.type !== "hidden";

  /* First (lowest index) round of the payload. */
  const firstRoundType = (payload) => {
    if (!Array.isArray(payload.rounds)) return "";
    const rounds = payload.rounds.filter((round) => round && typeof round === "object");
    if (rounds.length === 0) return "";
    rounds.sort((first, second) => (Number(first.index) || 0) - (Number(second.index) || 0));
    return typeof rounds[0].type === "string" ? rounds[0].type : "";
  };

  const escapeForRegExp = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

  /* Whole-word-ish match so "sql" does not match "mysql" inside unrelated prose. */
  const containsKeyword = (text, keyword) => {
    if (!keyword) return false;
    return new RegExp("(^|[^a-z0-9])" + escapeForRegExp(keyword) + "([^a-z0-9]|$)", "i").test(text);
  };

  const topicQuestionType = (topic) => {
    if (typeof topic !== "string") return "";
    const normalised = topic.trim().toLowerCase();
    if (!normalised) return "";
    const values = Object.keys(PREFILL_MAP.topicKeywords);
    for (let index = 0; index < values.length; index += 1) {
      if (values[index] === normalised) return values[index];
    }
    for (let index = 0; index < values.length; index += 1) {
      const keywords = PREFILL_MAP.topicKeywords[values[index]];
      for (let keywordIndex = 0; keywordIndex < keywords.length; keywordIndex += 1) {
        if (containsKeyword(normalised, keywords[keywordIndex])) return values[index];
      }
    }
    return "";
  };

  /* The optional questions live in a collapsed <details>; reveal it so the visitor sees
     what was added (the form's own progressive enhancement does the same for errors). */
  const revealCollapsedPanel = (control) => {
    const panel = control.closest ? control.closest("details") : null;
    if (panel && !panel.open) panel.open = true;
  };

  const statusForResult = (result) => {
    const parts = [];
    if (result.changed > 0) {
      parts.push(formatCount(MESSAGE.appliedCount, result.changed) || MESSAGE.applied);
    } else if (result.considered > 0) {
      parts.push(MESSAGE.unchanged);
    } else {
      parts.push(MESSAGE.noMatch || MESSAGE.unavailable);
    }
    if (result.skipped > 0) parts.push(formatCount(MESSAGE.skipped, result.skipped));
    return joinText(parts);
  };

  /* Copy a reviewed draft into the form. Returns {considered, changed, skipped,
     firstChanged}; nothing is ever submitted. */
  const applyPrefill = (payload) => {
    const result = { considered: 0, changed: 0, skipped: 0, firstChanged: null };

    const remember = (control) => {
      if (control && !result.firstChanged) result.firstChanged = control;
    };

    /* 1. Plain text fields: fill only when empty and only when the value fits maxlength. */
    PREFILL_MAP.text.forEach((entry) => {
      const raw = payload[entry.source];
      if (typeof raw !== "string") return;
      const value = raw.trim();
      if (!value) return;
      const input = form.querySelector(entry.target);
      if (!input || !isUsableControl(input)) return;
      result.considered += 1;
      if (input.value.trim() !== "") return; // already answered: never overwrite
      const maxLength = Number.parseInt(input.getAttribute("maxlength"), 10);
      if (!Number.isNaN(maxLength) && maxLength > 0 && value.length > maxLength) {
        result.skipped += 1; // would not validate: leave it for the visitor
        return;
      }
      input.value = value;
      dispatch(input, "input");
      dispatch(input, "change");
      result.changed += 1;
      remember(input);
    });

    /* 2. Radio groups: choose the mapped option only while the group is unanswered. */
    PREFILL_MAP.radios.forEach((entry) => {
      const raw = entry.fromFirstRound ? firstRoundType(payload) : payload[entry.source];
      const normalised = typeof raw === "string" ? raw.trim().toUpperCase() : "";
      const targetValue = normalised ? entry.values[normalised] : "";
      if (!targetValue) return;
      const inputs = groupInputs(entry.name);
      if (inputs.length === 0) return;
      const control = inputs.filter((input) => input.value === targetValue)[0];
      if (!control || !isUsableControl(control)) return;
      result.considered += 1;
      if (inputs.some((input) => input.checked)) return; // already answered: keep it
      control.checked = true;
      dispatch(control, "change");
      result.changed += 1;
      remember(control);
    });

    /* 3. Round topics -> questionTypes checkboxes. Additive only: never unchecks. */
    const matchedTypes = Object.create(null);
    if (Array.isArray(payload.rounds)) {
      payload.rounds.forEach((round) => {
        if (!round || typeof round !== "object" || !Array.isArray(round.topics)) return;
        round.topics.forEach((topic) => {
          const value = topicQuestionType(topic);
          if (!value || matchedTypes[value]) return;
          matchedTypes[value] = true;
          const control = form.querySelector(
            'input[name="' + PREFILL_MAP.checkboxGroup + '"][value="' + value + '"]'
          );
          if (!control || control.type !== "checkbox" || !isUsableControl(control)) return;
          result.considered += 1;
          if (control.checked) return;
          revealCollapsedPanel(control);
          control.checked = true;
          dispatch(control, "change");
          result.changed += 1;
          remember(control);
        });
      });
    }

    return result;
  };

  const signatureOf = (payload) => {
    try {
      return JSON.stringify(payload);
    } catch (ignored) {
      return "";
    }
  };

  const focusFirstChanged = (control) => {
    if (!control || typeof control.focus !== "function") return;
    try {
      control.focus({ preventScroll: false });
    } catch (ignored) {
      /* older engines: focus without options */
      try {
        control.focus();
      } catch (alsoIgnored) {
        /* keep the visitor where they are */
      }
    }
  };

  /* ------------------------------------------------------------------
     Request / reply state
     ------------------------------------------------------------------ */
  const setBusy = (busy) => {
    applyButton.disabled = busy;
    if (busy) applyButton.setAttribute("aria-busy", "true");
    else applyButton.removeAttribute("aria-busy");
  };

  const clearPending = () => {
    if (pendingTimer !== null) {
      window.clearTimeout(pendingTimer);
      pendingTimer = null;
    }
    pendingRequestId = null;
    setBusy(false);
  };

  const requestPrefill = () => {
    if (applyButton.disabled) return;
    const requestId =
      "prefill-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 8);
    pendingRequestId = requestId;
    setBusy(true);
    setStatus(MESSAGE.checking);

    let sent = false;
    try {
      const target = iframe.contentWindow;
      if (target) {
        target.postMessage({ type: "oiep-ai:request-prefill", requestId: requestId }, widgetOrigin);
        sent = true;
      }
    } catch (ignored) {
      sent = false;
    }

    if (!sent) {
      clearPending();
      setStatus(MESSAGE.unavailable);
      showFallback();
      return;
    }

    pendingTimer = window.setTimeout(() => {
      clearPending();
      setStatus(MESSAGE.unavailable);
      showFallback();
    }, REQUEST_TIMEOUT_MS);
  };

  /* ------------------------------------------------------------------
     Origin-checked postMessage hand-off from the widget.
     Accepts only the two documented message types, only from the exact
     configured origin and only from the embedded iframe's window.
     ------------------------------------------------------------------ */
  const onWidgetMessage = (event) => {
    if (!event || event.origin !== widgetOrigin) return;
    if (!iframe.contentWindow || event.source !== iframe.contentWindow) return;

    const data = event.data;
    if (!data || typeof data !== "object" || typeof data.type !== "string") return;

    const replyRequestId = typeof data.requestId === "string" ? data.requestId : null;
    if (
      pendingRequestId !== null &&
      replyRequestId !== null &&
      replyRequestId !== pendingRequestId
    ) {
      return; // stale reply to an older request
    }

    if (data.type === "oiep-ai:prefill-unavailable") {
      clearPending();
      setStatus(MESSAGE.notReady || MESSAGE.unavailable);
      return;
    }

    if (data.type !== "oiep-ai:prefill") return;

    clearPending();
    hideFallback();

    const signature = signatureOf(data.payload);

    /* Duplicate guard: the widget may post the same draft again (auto-post after a
       second finalize). Re-applying would be a no-op, but it must not re-announce
       changes; keep the current status instead. */
    if (signature && signature === lastAppliedSignature && lastAppliedResult) {
      if (!statusEl.textContent) setStatus(statusForResult(lastAppliedResult));
      return;
    }

    const result = applyPrefill(
      data.payload && typeof data.payload === "object" ? data.payload : {}
    );
    lastAppliedSignature = signature;
    lastAppliedResult = result;

    setStatus(statusForResult(result));
    if (result.changed > 0) focusFirstChanged(result.firstChanged);
  };

  /* ------------------------------------------------------------------
     Floating widget: open/close the compact panel from the launcher.
     Progressive enhancement only — the launcher is an <a> to the standalone
     widget until this script takes over (modified clicks still open that tab).
     ------------------------------------------------------------------ */
  const launcher = root.querySelector("[data-companion-launcher]");
  const panel = root.querySelector("#companion-helper-panel");
  const closeButton = root.querySelector("[data-companion-close]");
  let wasFocusedBeforeOpen = null;

  const setPanelOpen = (open) => {
    if (!panel) return;
    panel.hidden = !open;
    root.classList.toggle("is-open", open);
    if (launcher) {
      launcher.setAttribute("aria-expanded", open ? "true" : "false");
      // The round button swaps between the chat icon and a close icon; keep its
      // accessible name in sync (both labels come from the server-rendered bundle).
      const label = root.getAttribute(open ? "data-msg-close" : "data-msg-launcher");
      if (label) {
        launcher.setAttribute("aria-label", label);
        launcher.setAttribute("title", label);
      }
    }
  };

  const openPanel = () => {
    if (!panel || !panel.hidden) return;
    wasFocusedBeforeOpen = document.activeElement;
    setPanelOpen(true);
    const firstFocusable = closeButton || panel;
    if (firstFocusable && typeof firstFocusable.focus === "function") {
      try {
        firstFocusable.focus({ preventScroll: true });
      } catch (ignored) {
        try {
          firstFocusable.focus();
        } catch (alsoIgnored) {
          /* keep the visitor where they are */
        }
      }
    }
  };

  const closePanel = (restoreFocus) => {
    if (!panel || panel.hidden) return;
    setPanelOpen(false);
    if (!restoreFocus) return;
    const target = launcher || wasFocusedBeforeOpen;
    if (target && typeof target.focus === "function") {
      try {
        target.focus({ preventScroll: true });
      } catch (ignored) {
        try {
          target.focus();
        } catch (alsoIgnored) {
          /* keep the visitor where they are */
        }
      }
    }
  };

  if (launcher && panel) {
    launcher.addEventListener("click", (event) => {
      // Modified clicks (new tab/window) keep the plain-link behaviour.
      if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return;
      }
      event.preventDefault();
      if (panel.hidden) openPanel();
      else closePanel(true);
    });

    if (closeButton) closeButton.addEventListener("click", () => closePanel(true));

    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && !panel.hidden) closePanel(true);
    });

    // Click outside the panel (and its launcher) closes it without stealing focus.
    document.addEventListener("click", (event) => {
      if (panel.hidden) return;
      const target = event.target;
      if (target && (panel.contains(target) || launcher.contains(target))) return;
      closePanel(false);
    });
  }

  applyButton.addEventListener("click", requestPrefill);
  window.addEventListener("message", onWidgetMessage);
})();
