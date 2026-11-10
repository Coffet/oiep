<%-- Optional AI companion component (operator-only): AI helper view companion/admin-assistant.
     Loads its own external CSS/JS after the shared head; no inline script, style or handlers,
     because the application CSP only authorises its fixed inline assets by hash. --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" session="false" %>
<%@ include file="../fragments/taglibs.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="isZh" value="${fn:startsWith(pageLang, 'zh')}"/>
<c:set var="adminCss" value="true"/>
<!DOCTYPE html>
<html lang="${pageLang}" class="no-js">
<head>
<%@ include file="../fragments/head.jspf" %>
<link rel="stylesheet" href="${ctx}/css/companion-admin.css">
<script src="${ctx}/js/companion-admin.js" defer></script>
</head>
<body class="page page--admin page--companion-admin">
<%@ include file="../fragments/admin-topbar.jspf" %>
<main id="main" class="admin-main admin-main--narrow" tabindex="-1">
  <header class="admin-hero">
    <p class="admin-kicker">
      <span class="companion-admin__badge"><spring:message code="companion.admin.readOnly"/></span>
      <span class="companion-admin__badge companion-admin__badge--quiet"><spring:message code="companion.admin.optional"/></span>
    </p>
    <h1 class="display"><spring:message code="companion.admin.title"/></h1>
    <p class="admin-lead"><spring:message code="companion.admin.lead"/></p>
  </header>

  <div id="companion-admin" class="companion-admin"
       data-api-base="<c:out value='${companionApiBase}'/>"
       data-csrf="<c:out value='${adminCsrfToken}'/>"
       data-msg-busy="<spring:message code='companion.admin.busy' htmlEscape='true'/>"
       data-msg-error="<spring:message code='companion.admin.error' htmlEscape='true'/>"
       data-msg-csrf="<spring:message code='companion.admin.error.csrf' htmlEscape='true'/>"
       data-msg-request="<spring:message code='companion.admin.error.request' htmlEscape='true'/>"
       data-msg-response="<spring:message code='companion.admin.response' htmlEscape='true'/>"
       data-msg-generated-by="<spring:message code='companion.admin.chat.generatedBy' htmlEscape='true'/>"
       data-msg-count="<spring:message code='companion.admin.query.count' htmlEscape='true'/>"
       data-msg-truncated="<spring:message code='companion.admin.query.truncated' htmlEscape='true'/>"
       data-msg-audit-time="<spring:message code='companion.admin.audit.time' htmlEscape='true'/>"
       data-msg-audit-action="<spring:message code='companion.admin.audit.action' htmlEscape='true'/>"
       data-msg-audit-outcome="<spring:message code='companion.admin.audit.outcome' htmlEscape='true'/>"
       data-msg-audit-duration="<spring:message code='companion.admin.audit.duration' htmlEscape='true'/>"
       data-msg-unavailable="<spring:message code='companion.admin.health.unavailable' htmlEscape='true'/>">

    <%-- 1. Status / health -------------------------------------------------- --%>
    <section class="companion-admin__panel" aria-labelledby="companion-health-title">
      <div class="companion-admin__panel-head">
        <h2 class="companion-admin__title" id="companion-health-title"><spring:message code="companion.admin.health.title"/></h2>
        <button class="admin-btn admin-btn--small" id="health-refresh" type="button"><spring:message code="companion.admin.health.refresh"/></button>
      </div>
      <p class="companion-admin__status" id="health-status" role="status" aria-live="polite"></p>
      <dl class="companion-admin__facts" id="health-facts" hidden>
        <div class="companion-admin__fact">
          <dt><spring:message code="companion.admin.health.corpus"/></dt>
          <dd id="health-corpus"></dd>
        </div>
        <div class="companion-admin__fact">
          <dt><spring:message code="companion.admin.health.lastSync"/></dt>
          <dd id="health-last-sync"></dd>
        </div>
        <div class="companion-admin__fact">
          <dt><spring:message code="companion.admin.health.model"/></dt>
          <dd id="health-model"></dd>
        </div>
        <div class="companion-admin__fact">
          <dt><spring:message code="companion.admin.health.tools"/></dt>
          <dd id="health-tools"></dd>
        </div>
      </dl>
      <p class="companion-admin__warn" id="health-degraded" role="status" hidden><spring:message code="companion.admin.health.degraded"/></p>
      <p class="companion-admin__note"><spring:message code="companion.admin.health.privacy"/></p>
    </section>

    <%-- 2. Ask the corpus ---------------------------------------------------- --%>
    <section class="companion-admin__panel" aria-labelledby="companion-chat-title">
      <div class="companion-admin__panel-head">
        <h2 class="companion-admin__title" id="companion-chat-title"><spring:message code="companion.admin.chat.title"/></h2>
        <button class="admin-btn admin-btn--small" id="chat-clear" type="button"><spring:message code="companion.admin.chat.clear"/></button>
      </div>
      <form class="companion-admin__form" id="companion-chat-form" novalidate>
        <label class="companion-admin__label" for="chat-input"><spring:message code="companion.admin.chat.label"/></label>
        <textarea class="companion-admin__textarea" id="chat-input" name="message" rows="3" maxlength="4000"
                  placeholder="<spring:message code='companion.admin.chat.placeholder' htmlEscape='true'/>"></textarea>
        <div class="companion-admin__actions">
          <button class="admin-btn admin-btn--primary" id="chat-submit" type="button"><spring:message code="companion.admin.chat.send"/></button>
          <span class="companion-admin__status" id="chat-status" role="status" aria-live="polite"></span>
        </div>
      </form>
      <div class="companion-admin__output" id="chat-output" hidden>
        <h3 class="companion-admin__subtitle"><spring:message code="companion.admin.chat.answer"/></h3>
        <p class="companion-admin__answer" id="chat-answer" tabindex="-1"></p>
        <p class="companion-admin__meta" id="chat-generated" hidden></p>
        <div id="chat-tools-wrap" hidden>
          <h4 class="companion-admin__subtitle companion-admin__subtitle--minor"><spring:message code="companion.admin.chat.tools"/></h4>
          <ul class="companion-admin__list" id="chat-tools"></ul>
        </div>
        <div id="chat-coverage-wrap" hidden>
          <h4 class="companion-admin__subtitle companion-admin__subtitle--minor"><spring:message code="companion.admin.chat.coverage"/></h4>
          <p class="companion-admin__meta" id="chat-coverage"></p>
        </div>
        <div id="chat-data-wrap" hidden>
          <h4 class="companion-admin__subtitle companion-admin__subtitle--minor"><spring:message code="companion.admin.chat.data"/></h4>
          <pre class="companion-admin__pre" id="chat-data"></pre>
        </div>
      </div>
      <p class="companion-admin__note"><spring:message code="companion.admin.chat.privacy"/></p>
    </section>

    <%-- 3. Structured query -------------------------------------------------- --%>
    <section class="companion-admin__panel" aria-labelledby="companion-query-title">
      <div class="companion-admin__panel-head">
        <h2 class="companion-admin__title" id="companion-query-title"><spring:message code="companion.admin.query.title"/></h2>
      </div>
      <p class="companion-admin__note"><spring:message code="companion.admin.query.hint"/></p>
      <form class="companion-admin__form" id="companion-query-form" novalidate>
        <label class="companion-admin__label" for="query-input"><spring:message code="companion.admin.query.label"/></label>
        <textarea class="companion-admin__textarea companion-admin__textarea--code" id="query-input" name="query" rows="6" spellcheck="false"></textarea>
        <div class="companion-admin__actions">
          <button class="admin-btn admin-btn--primary" id="query-submit" type="button"><spring:message code="companion.admin.query.run"/></button>
          <span class="companion-admin__status" id="query-status" role="status" aria-live="polite"></span>
        </div>
      </form>
      <div class="companion-admin__output" id="query-output" hidden>
        <p class="companion-admin__meta" id="query-meta"></p>
        <div class="companion-admin__table-wrap" id="query-table-wrap" hidden>
          <table class="companion-admin__table" id="query-table">
            <caption class="sr-only"><spring:message code="companion.admin.query.rows"/></caption>
          </table>
        </div>
      </div>
    </section>

    <%-- 4. Deterministic summary -------------------------------------------- --%>
    <section class="companion-admin__panel" aria-labelledby="companion-summarise-title">
      <div class="companion-admin__panel-head">
        <h2 class="companion-admin__title" id="companion-summarise-title"><spring:message code="companion.admin.summarise.title"/></h2>
      </div>
      <form class="companion-admin__form" id="companion-summarise-form" novalidate>
        <label class="companion-admin__label" for="summarise-focus"><spring:message code="companion.admin.summarise.focus"/></label>
        <select class="companion-admin__select" id="summarise-focus" name="focus">
          <option value="overview"><spring:message code="companion.admin.focus.overview"/></option>
          <option value="outcomes"><spring:message code="companion.admin.focus.outcomes"/></option>
          <option value="rounds"><spring:message code="companion.admin.focus.rounds"/></option>
          <option value="locations"><spring:message code="companion.admin.focus.locations"/></option>
          <option value="difficulty"><spring:message code="companion.admin.focus.difficulty"/></option>
          <option value="recent"><spring:message code="companion.admin.focus.recent"/></option>
        </select>
        <label class="companion-admin__label" for="summarise-filters"><spring:message code="companion.admin.summarise.filters"/></label>
        <textarea class="companion-admin__textarea companion-admin__textarea--code" id="summarise-filters" name="filters" rows="2" spellcheck="false" placeholder="{}"></textarea>
        <div class="companion-admin__actions">
          <button class="admin-btn admin-btn--primary" id="summarise-submit" type="button"><spring:message code="companion.admin.summarise.run"/></button>
          <span class="companion-admin__status" id="summarise-status" role="status" aria-live="polite"></span>
        </div>
      </form>
      <div class="companion-admin__output" id="summarise-output" hidden>
        <h3 class="companion-admin__subtitle"><spring:message code="companion.admin.summarise.headline"/></h3>
        <p class="companion-admin__answer" id="summarise-headline" tabindex="-1"></p>
        <div id="summarise-bullets-wrap" hidden>
          <h4 class="companion-admin__subtitle companion-admin__subtitle--minor"><spring:message code="companion.admin.summarise.bullets"/></h4>
          <ul class="companion-admin__list" id="summarise-bullets"></ul>
        </div>
        <div id="summarise-coverage-wrap" hidden>
          <h4 class="companion-admin__subtitle companion-admin__subtitle--minor"><spring:message code="companion.admin.chat.coverage"/></h4>
          <p class="companion-admin__meta" id="summarise-coverage"></p>
        </div>
        <div id="summarise-data-wrap" hidden>
          <h4 class="companion-admin__subtitle companion-admin__subtitle--minor"><spring:message code="companion.admin.chat.data"/></h4>
          <pre class="companion-admin__pre" id="summarise-data"></pre>
        </div>
      </div>
    </section>

    <%-- 5. Corpus sync & audit ---------------------------------------------- --%>
    <section class="companion-admin__panel" aria-labelledby="companion-sync-title">
      <div class="companion-admin__panel-head">
        <h2 class="companion-admin__title" id="companion-sync-title"><spring:message code="companion.admin.sync.title"/></h2>
      </div>
      <dl class="companion-admin__facts" aria-label="<spring:message code='companion.admin.sync.state'/>">
        <div class="companion-admin__fact">
          <dt><spring:message code="companion.admin.sync.configured"/></dt>
          <dd id="sync-configured"></dd>
        </div>
        <div class="companion-admin__fact">
          <dt><spring:message code="companion.admin.health.corpus"/></dt>
          <dd id="sync-corpus"></dd>
        </div>
        <div class="companion-admin__fact">
          <dt><spring:message code="companion.admin.sync.state"/></dt>
          <dd id="sync-state"></dd>
        </div>
        <div class="companion-admin__fact">
          <dt><spring:message code="companion.admin.health.lastSync"/></dt>
          <dd id="sync-last-sync"></dd>
        </div>
        <div class="companion-admin__fact">
          <dt><spring:message code="companion.admin.sync.inserted"/></dt>
          <dd id="sync-inserted"></dd>
        </div>
        <div class="companion-admin__fact">
          <dt><spring:message code="companion.admin.sync.updated"/></dt>
          <dd id="sync-updated"></dd>
        </div>
        <div class="companion-admin__fact">
          <dt><spring:message code="companion.admin.sync.deleted"/></dt>
          <dd id="sync-deleted"></dd>
        </div>
      </dl>
      <div class="companion-admin__actions">
        <button class="admin-btn admin-btn--small" id="sync-incremental" type="button"><spring:message code="companion.admin.sync.incremental"/></button>
        <button class="admin-btn admin-btn--small admin-btn--danger" id="sync-full" type="button"><spring:message code="companion.admin.sync.full"/></button>
        <span class="companion-admin__status" id="sync-status" role="status" aria-live="polite"></span>
      </div>
      <div id="sync-result-wrap" hidden>
        <h3 class="companion-admin__subtitle"><spring:message code="companion.admin.sync.result"/></h3>
        <pre class="companion-admin__pre" id="sync-result"></pre>
      </div>

      <h3 class="companion-admin__subtitle"><spring:message code="companion.admin.sync.audit"/></h3>
      <div class="companion-admin__actions">
        <button class="admin-btn admin-btn--small" id="audit-load" type="button"><spring:message code="companion.admin.sync.auditLoad"/></button>
        <span class="companion-admin__status" id="audit-status" role="status" aria-live="polite"></span>
      </div>
      <div class="companion-admin__table-wrap" id="audit-wrap" hidden>
        <table class="companion-admin__table" id="audit-table">
          <caption class="sr-only"><spring:message code="companion.admin.sync.audit"/></caption>
        </table>
      </div>
    </section>
  </div>
</main>
<%@ include file="../fragments/footer.jspf" %>
</body>
</html>
