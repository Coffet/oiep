/* OIEP — operator AI helper (optional companion component).
 *
 * Same-origin only: every request goes to /admin/assistant/api on this server. The companion
 * service's operator token lives on the server and is never exposed here, and no response data
 * is ever turned into markup: all rendering uses textContent only.
 *
 * The page works without this file (nothing is submitted by default); this only adds the calls.
 */
(() => {
  "use strict";

  const root = document.getElementById("companion-admin");
  if (!root) return;

  const data = root.dataset;
  const apiBase = data.apiBase || "";
  const csrfToken = data.csrf || "";

  const $ = (id) => document.getElementById(id);
  const msg = (key) => data[key] || "";

  const endpoints = {
    health: "/health",
    corpusSync: "/corpus/sync",
    chat: "/chat",
    query: "/query",
    summarise: "/summarise",
    audit: "/audit",
  };

  /* ------------------------------------------------------------ small helpers */

  const setHidden = (element, hidden) => {
    if (element) element.hidden = Boolean(hidden);
  };

  const setStatus = (element, message) => {
    if (element) element.textContent = message || "";
  };

  const setBusy = (element, busy) => {
    if (!element) return;
    element.disabled = Boolean(busy);
    if (busy) {
      element.setAttribute("aria-busy", "true");
    } else {
      element.removeAttribute("aria-busy");
    }
  };

  const formatCell = (value) => {
    if (value === null || value === undefined) return "—";
    if (typeof value === "object") return JSON.stringify(value);
    return String(value);
  };

  const errorParts = (error) => {
    const parts = [];
    if (error && error.status) parts.push(String(error.status));
    if (error && error.code) parts.push(error.code);
    if (error && error.message) parts.push(error.message);
    return parts.join(" · ");
  };

  /* Error codes are mapped to bundle strings; unknown server errors keep their status/code so
   * an operator can still diagnose the failure (companion validation errors are useful). */
  const errorText = (error) => {
    if (!error) return msg("msgError");
    if (error.code === "csrf_invalid") return msg("msgCsrf");
    if (error.code === "invalid_request"
        || error.code === "body_too_large"
        || error.code === "unsupported_media_type") {
      return msg("msgRequest");
    }
    if (error.code === "companion_unavailable" || error.code === "companion_auth_failed") {
      return msg("msgError");
    }
    if (error.status) {
      const parts = errorParts(error);
      return parts ? msg("msgRequest") + " (" + parts + ")" : msg("msgRequest");
    }
    return msg("msgError");
  };

  /* ------------------------------------------------------------ HTTP plumbing */

  const request = async (path, options) => {
    const settings = Object.assign(
      { method: "GET", credentials: "same-origin", headers: { Accept: "application/json" } },
      options || {}
    );
    const response = await fetch(apiBase + path, settings);
    const raw = await response.text();
    let payload = null;
    if (raw) {
      try {
        payload = JSON.parse(raw);
      } catch (ignored) {
        payload = null;
      }
    }
    if (!response.ok) {
      const envelope = payload && payload.error ? payload.error : {};
      const error = new Error(envelope.message || "");
      error.status = response.status;
      error.code = envelope.code || "";
      throw error;
    }
    return payload;
  };

  const postJson = (path, payload) => request(path, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      "X-OIEP-CSRF": csrfToken,
    },
    body: JSON.stringify(payload),
  });

  const readJsonField = (raw, fallback) => {
    const trimmed = (raw || "").trim();
    if (!trimmed) return fallback;
    return JSON.parse(trimmed);
  };

  const renderCoverage = (coverage, element, wrapper) => {
    if (!coverage) {
      setHidden(wrapper, true);
      return;
    }
    const matched = coverage.matched === undefined ? "—" : coverage.matched;
    const total = coverage.corpus_total === undefined ? "—" : coverage.corpus_total;
    const filters = Array.isArray(coverage.filters) ? coverage.filters.join(", ") : "";
    if (element) element.textContent = matched + " / " + total + (filters ? " · " + filters : "");
    setHidden(wrapper, false);
  };

  /* -------------------------------------------------------------------- health */

  const renderHealth = (payload) => {
    const health = payload || {};
    const corpus = health.corpus || {};
    const model = health.model || {};
    const tools = Array.isArray(health.tools) ? health.tools : [];

    const corpusEl = $("health-corpus");
    const syncEl = $("health-last-sync");
    const modelEl = $("health-model");
    const toolsEl = $("health-tools");
    if (corpusEl) corpusEl.textContent = formatCell(corpus.experiences);
    if (syncEl) {
      const at = corpus.last_sync_at ? String(corpus.last_sync_at) : "—";
      const state = corpus.last_status ? " · " + corpus.last_status : "";
      syncEl.textContent = at + state;
    }
    if (modelEl) {
      modelEl.textContent = model.backend ? String(model.backend) : "—";
    }
    if (toolsEl) toolsEl.textContent = tools.length ? tools.join(", ") : "—";

    setHidden($("health-facts"), false);
    setHidden($("health-degraded"), model.degraded !== true);
  };

  const loadHealth = async () => {
    const statusEl = $("health-status");
    setBusy($("health-refresh"), true);
    setStatus(statusEl, msg("msgBusy"));
    try {
      renderHealth(await request(endpoints.health));
      setStatus(statusEl, "");
    } catch (error) {
      setHidden($("health-facts"), true);
      setStatus(statusEl, msg("msgUnavailable"));
    } finally {
      setBusy($("health-refresh"), false);
    }
  };

  /* ---------------------------------------------------------------------- chat */

  let conversationId = null;

  const renderChat = (payload) => {
    const chat = payload || {};
    const answer = $("chat-answer");
    if (answer) {
      answer.textContent = chat.answer ? String(chat.answer) : "";
    }

    const generated = $("chat-generated");
    if (generated) {
      if (chat.generated_by) {
        generated.textContent = msg("msgGeneratedBy") + ": " + chat.generated_by;
        setHidden(generated, false);
      } else {
        generated.textContent = "";
        setHidden(generated, true);
      }
    }

    const toolCalls = Array.isArray(chat.tool_calls) ? chat.tool_calls : [];
    const toolsList = $("chat-tools");
    if (toolsList) toolsList.textContent = "";
    toolCalls.forEach((call) => {
      const item = document.createElement("li");
      const name = call && call.name ? call.name : "tool";
      const count = call && call.row_count !== undefined && call.row_count !== null
        ? " (" + call.row_count + ")"
        : "";
      const args = call && call.args ? " · " + JSON.stringify(call.args) : "";
      item.textContent = name + count + args;
      if (toolsList) toolsList.appendChild(item);
    });
    setHidden($("chat-tools-wrap"), toolCalls.length === 0);

    renderCoverage(chat.coverage, $("chat-coverage"), $("chat-coverage-wrap"));

    const rawData = chat.data === undefined ? null : chat.data;
    const dataEl = $("chat-data");
    if (rawData !== null) {
      if (dataEl) dataEl.textContent = JSON.stringify(rawData, null, 2);
      setHidden($("chat-data-wrap"), false);
    } else {
      if (dataEl) dataEl.textContent = "";
      setHidden($("chat-data-wrap"), true);
    }

    setHidden($("chat-output"), false);
    if (answer && typeof answer.focus === "function") answer.focus();
  };

  const sendChat = async () => {
    const input = $("chat-input");
    const statusEl = $("chat-status");
    const message = input ? input.value.trim() : "";
    if (!message) {
      setStatus(statusEl, msg("msgRequest"));
      if (input) input.focus();
      return;
    }

    setBusy($("chat-submit"), true);
    setStatus(statusEl, msg("msgBusy"));
    try {
      const payload = await postJson(endpoints.chat, {
        message: message,
        conversation_id: conversationId,
      });
      if (payload && typeof payload.conversation_id === "string" && payload.conversation_id) {
        conversationId = payload.conversation_id;
      }
      renderChat(payload);
      setStatus(statusEl, msg("msgResponse"));
    } catch (error) {
      setStatus(statusEl, errorText(error));
    } finally {
      setBusy($("chat-submit"), false);
    }
  };

  const clearChat = () => {
    conversationId = null;
    setHidden($("chat-output"), true);
    ["chat-answer", "chat-generated", "chat-coverage", "chat-data", "chat-tools"].forEach((id) => {
      const element = $(id);
      if (element) element.textContent = "";
    });
    setHidden($("chat-generated"), true);
    setHidden($("chat-tools-wrap"), true);
    setHidden($("chat-coverage-wrap"), true);
    setHidden($("chat-data-wrap"), true);
    setStatus($("chat-status"), "");
    const input = $("chat-input");
    if (input) input.focus();
  };

  /* --------------------------------------------------------------------- query */

  const renderQuery = (payload) => {
    const result = payload || {};
    const rows = Array.isArray(result.rows) ? result.rows : [];
    const rowCount = result.row_count === undefined ? rows.length : result.row_count;
    const truncated = result.truncated === true;

    const meta = $("query-meta");
    if (meta) {
      meta.textContent = msg("msgCount") + ": " + rowCount
        + (truncated ? " · " + msg("msgTruncated") : "");
    }

    const table = $("query-table");
    if (table) {
      table.textContent = "";
      if (rows.length > 0 && rows[0] && typeof rows[0] === "object") {
        const columns = Object.keys(rows[0]);
        const thead = document.createElement("thead");
        const headRow = document.createElement("tr");
        columns.forEach((column) => {
          const th = document.createElement("th");
          th.scope = "col";
          th.textContent = column;
          headRow.appendChild(th);
        });
        thead.appendChild(headRow);
        table.appendChild(thead);

        const tbody = document.createElement("tbody");
        rows.forEach((row) => {
          const tr = document.createElement("tr");
          columns.forEach((column) => {
            const td = document.createElement("td");
            td.textContent = formatCell(row ? row[column] : "");
            tr.appendChild(td);
          });
          tbody.appendChild(tr);
        });
        table.appendChild(tbody);
      }
    }

    setHidden($("query-table-wrap"), rows.length === 0);
    setHidden($("query-output"), false);
  };

  const runQuery = async () => {
    const input = $("query-input");
    const statusEl = $("query-status");
    let payload;
    try {
      payload = readJsonField(input ? input.value : "", {});
    } catch (error) {
      setStatus(statusEl, msg("msgRequest"));
      if (input) input.focus();
      return;
    }
    if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
      setStatus(statusEl, msg("msgRequest"));
      if (input) input.focus();
      return;
    }

    setBusy($("query-submit"), true);
    setStatus(statusEl, msg("msgBusy"));
    try {
      renderQuery(await postJson(endpoints.query, payload));
      setStatus(statusEl, msg("msgResponse"));
    } catch (error) {
      setStatus(statusEl, errorText(error));
    } finally {
      setBusy($("query-submit"), false);
    }
  };

  /* ----------------------------------------------------------------- summarise */

  const renderSummarise = (payload) => {
    const result = payload || {};
    const summary = result.summary || {};

    const headline = $("summarise-headline");
    if (headline) headline.textContent = summary.headline ? String(summary.headline) : "";

    const bullets = Array.isArray(summary.bullets) ? summary.bullets : [];
    const list = $("summarise-bullets");
    if (list) {
      list.textContent = "";
      bullets.forEach((bullet) => {
        const item = document.createElement("li");
        item.textContent = String(bullet);
        list.appendChild(item);
      });
    }
    setHidden($("summarise-bullets-wrap"), bullets.length === 0);

    renderCoverage(summary.coverage, $("summarise-coverage"), $("summarise-coverage-wrap"));

    const rawData = result.data === undefined ? null : result.data;
    const dataEl = $("summarise-data");
    if (rawData !== null) {
      if (dataEl) dataEl.textContent = JSON.stringify(rawData, null, 2);
      setHidden($("summarise-data-wrap"), false);
    } else {
      if (dataEl) dataEl.textContent = "";
      setHidden($("summarise-data-wrap"), true);
    }

    setHidden($("summarise-output"), false);
    if (headline && typeof headline.focus === "function") headline.focus();
  };

  const runSummarise = async () => {
    const filtersInput = $("summarise-filters");
    const focusSelect = $("summarise-focus");
    const statusEl = $("summarise-status");
    let filters;
    try {
      filters = readJsonField(filtersInput ? filtersInput.value : "", {});
    } catch (error) {
      setStatus(statusEl, msg("msgRequest"));
      if (filtersInput) filtersInput.focus();
      return;
    }
    if (!filters || typeof filters !== "object" || Array.isArray(filters)) {
      setStatus(statusEl, msg("msgRequest"));
      if (filtersInput) filtersInput.focus();
      return;
    }

    setBusy($("summarise-submit"), true);
    setStatus(statusEl, msg("msgBusy"));
    try {
      const payload = {
        filters: filters,
        focus: focusSelect ? focusSelect.value : "overview",
      };
      renderSummarise(await postJson(endpoints.summarise, payload));
      setStatus(statusEl, msg("msgResponse"));
    } catch (error) {
      setStatus(statusEl, errorText(error));
    } finally {
      setBusy($("summarise-submit"), false);
    }
  };

  /* -------------------------------------------------------------- corpus sync */

  const renderSyncState = (payload) => {
    const state = payload || {};
    const counts = state.last_counts || {};

    const configured = $("sync-configured");
    if (configured) {
      configured.textContent = state.configured === true ? "true"
        : state.configured === false ? "false" : "—";
    }
    const values = {
      "sync-corpus": state.corpus_total,
      "sync-state": state.last_status,
      "sync-last-sync": state.last_sync_at,
      "sync-inserted": counts.inserted,
      "sync-updated": counts.updated,
      "sync-deleted": counts.deleted,
    };
    Object.keys(values).forEach((id) => {
      const element = $(id);
      if (element) element.textContent = formatCell(values[id]);
    });
  };

  const loadSyncState = async () => {
    try {
      renderSyncState(await request(endpoints.corpusSync));
    } catch (error) {
      ["sync-configured", "sync-corpus", "sync-state", "sync-last-sync",
        "sync-inserted", "sync-updated", "sync-deleted"].forEach((id) => {
        const element = $(id);
        if (element) element.textContent = "—";
      });
    }
  };

  const triggerSync = async (full) => {
    const statusEl = $("sync-status");
    setBusy($("sync-incremental"), true);
    setBusy($("sync-full"), true);
    setStatus(statusEl, msg("msgBusy"));
    try {
      const result = await postJson(endpoints.corpusSync, { full: full === true });
      const resultEl = $("sync-result");
      if (resultEl) resultEl.textContent = JSON.stringify(result, null, 2);
      setHidden($("sync-result-wrap"), false);
      setStatus(statusEl, msg("msgResponse"));
      await loadSyncState();
    } catch (error) {
      setStatus(statusEl, errorText(error));
    } finally {
      setBusy($("sync-incremental"), false);
      setBusy($("sync-full"), false);
    }
  };

  /* ---------------------------------------------------------------------- audit */

  const renderAudit = (payload) => {
    const entries = payload && Array.isArray(payload.entries) ? payload.entries : [];
    const table = $("audit-table");
    if (table) {
      table.textContent = "";
      const columns = [
        { key: "created_at", label: msg("msgAuditTime"), suffix: "" },
        { key: "action", label: msg("msgAuditAction"), suffix: "" },
        { key: "outcome", label: msg("msgAuditOutcome"), suffix: "" },
        { key: "duration_ms", label: msg("msgAuditDuration"), suffix: " ms" },
      ];

      const thead = document.createElement("thead");
      const headRow = document.createElement("tr");
      columns.forEach((column) => {
        const th = document.createElement("th");
        th.scope = "col";
        th.textContent = column.label;
        headRow.appendChild(th);
      });
      thead.appendChild(headRow);
      table.appendChild(thead);

      const tbody = document.createElement("tbody");
      entries.forEach((entry) => {
        const tr = document.createElement("tr");
        columns.forEach((column) => {
          const td = document.createElement("td");
          const value = entry ? entry[column.key] : "";
          td.textContent = value === null || value === undefined
            ? "—"
            : String(value) + column.suffix;
          tr.appendChild(td);
        });
        tbody.appendChild(tr);
      });
      table.appendChild(tbody);
    }
    setHidden($("audit-wrap"), entries.length === 0);
  };

  const loadAudit = async () => {
    const statusEl = $("audit-status");
    setBusy($("audit-load"), true);
    setStatus(statusEl, msg("msgBusy"));
    try {
      renderAudit(await request(endpoints.audit));
      setStatus(statusEl, "");
    } catch (error) {
      setHidden($("audit-wrap"), true);
      setStatus(statusEl, errorText(error));
    } finally {
      setBusy($("audit-load"), false);
    }
  };

  /* ------------------------------------------------------------------- wiring */

  const on = (id, event, handler) => {
    const element = $(id);
    if (element) element.addEventListener(event, handler);
  };

  on("health-refresh", "click", loadHealth);
  on("chat-submit", "click", sendChat);
  on("chat-clear", "click", clearChat);
  on("query-submit", "click", runQuery);
  on("summarise-submit", "click", runSummarise);
  on("sync-incremental", "click", () => triggerSync(false));
  on("sync-full", "click", () => triggerSync(true));
  on("audit-load", "click", loadAudit);

  /* These forms are progressive enhancement only; without this file nothing is submitted, so a
   * visitor's typed question can never end up in a URL or a server log. */
  ["companion-chat-form", "companion-query-form", "companion-summarise-form"].forEach((id) => {
    const form = $(id);
    if (form) form.addEventListener("submit", (event) => event.preventDefault());
  });

  loadHealth();
  loadSyncState();
})();
