<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" session="false" %>
<%@ include file="../fragments/taglibs.jspf" %>
<%--
  Optional AI companion — user helper as a floating web-chat widget.

  Rendered by the generic pageExtensions loop in index.jsp only while the companion user
  helper is enabled and its widget URL is valid: no bean = no include = no markup.

  No JavaScript: the launcher is a plain link that opens the standalone widget (/ui/) in a new
  tab, and the panel stays hidden. With JavaScript the launcher becomes a chat-bubble toggle
  for the same widget embedded in a compact floating panel; a "Use the helper's answers"
  button asks the widget for the reviewed draft and fills matching form fields. Nothing is
  ever submitted and the form is only filled where it is still empty.

  Self-contained: its own stylesheet/script, escaped dynamic values, no inline script/style or
  event handlers (the CSP is hash-based), every string from messages-companion (EN / zh-HK).
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="helper" value="${companionUserHelper}"/>
<c:if test="${not empty helper and helper.available}">
  <spring:message code="companion.helper.kicker" var="msgKicker"/>
  <spring:message code="companion.helper.launcher" var="msgLauncher"/>
  <spring:message code="companion.helper.close" var="msgClose"/>
  <spring:message code="companion.helper.title" var="msgTitle"/>
  <spring:message code="companion.helper.lead" var="msgLead"/>
  <spring:message code="companion.helper.privacy" var="msgPrivacy"/>
  <spring:message code="companion.helper.optional" var="msgOptional"/>
  <spring:message code="companion.helper.iframeTitle" var="msgIframeTitle"/>
  <spring:message code="companion.helper.loadingHint" var="msgLoadingHint"/>
  <spring:message code="companion.helper.apply" var="msgApply"/>
  <spring:message code="companion.helper.prefillNote" var="msgPrefillNote"/>
  <spring:message code="companion.helper.fallback" var="msgFallback"/>
  <spring:message code="companion.helper.checking" var="msgChecking"/>
  <spring:message code="companion.helper.applied" var="msgApplied"/>
  <spring:message code="companion.helper.appliedCount" var="msgAppliedCount"/>
  <spring:message code="companion.helper.unchanged" var="msgUnchanged"/>
  <spring:message code="companion.helper.noMatch" var="msgNoMatch"/>
  <spring:message code="companion.helper.skipped" var="msgSkipped"/>
  <spring:message code="companion.helper.notReady" var="msgNotReady"/>
  <spring:message code="companion.helper.unavailable" var="msgUnavailable"/>
  <link rel="stylesheet" href="${ctx}/css/companion.css">
  <div class="companion-helper" data-companion-helper
      data-widget-origin="${fn:escapeXml(helper.widgetOrigin)}"
      data-msg-checking="${fn:escapeXml(msgChecking)}"
      data-msg-applied="${fn:escapeXml(msgApplied)}"
      data-msg-applied-count="${fn:escapeXml(msgAppliedCount)}"
      data-msg-unchanged="${fn:escapeXml(msgUnchanged)}"
      data-msg-no-match="${fn:escapeXml(msgNoMatch)}"
      data-msg-skipped="${fn:escapeXml(msgSkipped)}"
      data-msg-not-ready="${fn:escapeXml(msgNotReady)}"
      data-msg-unavailable="${fn:escapeXml(msgUnavailable)}">

    <%-- Launcher. Without JavaScript this is the fallback: it opens the standalone widget
         (the same /ui/ page) in a new tab. With JavaScript it toggles the panel below. --%>
    <a class="companion-helper__launcher" href="${fn:escapeXml(helper.widgetUrl)}"
        target="_blank" rel="noopener noreferrer"
        data-companion-launcher
        aria-controls="companion-helper-panel"
        aria-expanded="false">
      <svg class="companion-helper__bubble" width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
        <path d="M2 3.5h16v10H8.5L4 17.5V13.5H2v-10Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/>
        <path d="M6 7h8M6 9.6h5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
      </svg>
      <span class="companion-helper__launcher-label"><c:out value="${msgLauncher}"/></span>
    </a>

    <%-- Floating panel: hidden until the visitor opens the launcher (JS), or opens the
         standalone widget via the launcher link (no JS). --%>
    <div class="companion-helper__panel" id="companion-helper-panel" role="dialog" aria-modal="false"
        aria-labelledby="companion-helper-heading" aria-describedby="companion-helper-lead" hidden>
      <div class="companion-helper__head">
        <p class="companion-helper__kicker"><c:out value="${msgKicker}"/></p>
        <div class="companion-helper__title-row">
          <h2 class="companion-helper__title" id="companion-helper-heading"><c:out value="${msgTitle}"/></h2>
          <span class="companion-helper__optional"><c:out value="${msgOptional}"/></span>
        </div>
        <button class="companion-helper__close" type="button" data-companion-close
            aria-label="<c:out value='${msgClose}'/>">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true"><path d="M2 2l10 10M12 2L2 12" stroke="currentColor" stroke-width="1.6"/></svg>
        </button>
      </div>

      <p class="companion-helper__lead" id="companion-helper-lead"><c:out value="${msgLead}"/></p>

      <div class="companion-helper__frame">
        <iframe class="companion-helper__iframe"
            data-companion-widget
            src="${fn:escapeXml(helper.widgetUrl)}"
            title="${fn:escapeXml(msgIframeTitle)}"
            loading="lazy"
            referrerpolicy="no-referrer"
            sandbox="allow-scripts allow-forms allow-same-origin"
            allow="clipboard-write"
            aria-describedby="companion-helper-hint"></iframe>
      </div>
      <p class="companion-helper__hint" id="companion-helper-hint"><c:out value="${msgLoadingHint}"/></p>

      <div class="companion-helper__actions">
        <button class="companion-helper__apply" type="button" data-companion-apply aria-describedby="companion-helper-note">
          <span><c:out value="${msgApply}"/></span>
          <svg class="companion-helper__arrow" width="16" height="12" viewBox="0 0 18 14" fill="none" aria-hidden="true"><path d="M1 7h15M10 1l6 6-6 6" stroke="currentColor" stroke-width="1.5"/></svg>
        </button>
        <p class="companion-helper__note"><c:out value="${msgPrefillNote}"/></p>
      </div>

      <p class="companion-helper__status" role="status" aria-live="polite" data-companion-status></p>
      <p class="companion-helper__fallback" data-companion-fallback hidden><c:out value="${msgFallback}"/></p>

      <p class="companion-helper__privacy">
        <svg class="companion-helper__lock" width="12" height="14" viewBox="0 0 15 17" fill="none" aria-hidden="true"><rect x="1" y="7" width="13" height="9" stroke="currentColor"/><path d="M4 7V4.5a3.5 3.5 0 0 1 7 0V7" stroke="currentColor"/></svg>
        <span><c:out value="${msgPrivacy}"/></span>
      </p>
    </div>
  </div>
  <script src="${ctx}/js/companion.js" defer></script>
</c:if>
