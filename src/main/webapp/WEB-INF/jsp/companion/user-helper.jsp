<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" session="false" %>
<%@ include file="../fragments/taglibs.jspf" %>
<%--
  Optional AI companion — user helper as a floating chat widget.

  Rendered by the generic pageExtensions loop in index.jsp only while the companion user
  helper is enabled and its widget URL is valid: no bean = no include = no markup.

  A round chat button floats in the bottom-right corner. Clicking it opens a closable chat
  overlay (dialog) that embeds the companion's chat widget; Escape, the close button or an
  outside click collapses it again. Without JavaScript the button is a plain link that opens
  the standalone widget (/ui/) in a new tab and the overlay stays hidden.

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
  <link rel="stylesheet" href="${ctx}/css/companion.css?v=20261112b">
  <div class="companion-helper" data-companion-helper
      data-widget-origin="${fn:escapeXml(helper.widgetOrigin)}"
      data-msg-launcher="${fn:escapeXml(msgLauncher)}"
      data-msg-close="${fn:escapeXml(msgClose)}"
      data-msg-checking="${fn:escapeXml(msgChecking)}"
      data-msg-applied="${fn:escapeXml(msgApplied)}"
      data-msg-applied-count="${fn:escapeXml(msgAppliedCount)}"
      data-msg-unchanged="${fn:escapeXml(msgUnchanged)}"
      data-msg-no-match="${fn:escapeXml(msgNoMatch)}"
      data-msg-skipped="${fn:escapeXml(msgSkipped)}"
      data-msg-not-ready="${fn:escapeXml(msgNotReady)}"
      data-msg-unavailable="${fn:escapeXml(msgUnavailable)}">

    <%-- Round chat button. Without JavaScript this opens the standalone widget in a new
         tab; with JavaScript it toggles the chat overlay below and swaps to a close icon. --%>
    <a class="companion-helper__launcher" href="${fn:escapeXml(helper.widgetUrl)}"
        target="_blank" rel="noopener noreferrer"
        data-companion-launcher
        aria-controls="companion-helper-panel"
        aria-expanded="false"
        aria-label="<c:out value='${msgLauncher}'/>"
        title="<c:out value='${msgLauncher}'/>">
      <svg class="companion-helper__bubble" width="26" height="26" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M3 4.5h18v12H9.5L4.8 20V16.5H3v-12Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
        <path d="M7.5 8.5h9M7.5 11.4h6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
      </svg>
      <svg class="companion-helper__dismiss" width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="1.8"/>
      </svg>
    </a>

    <%-- Chat overlay: hidden until the visitor opens it (JS). --%>
    <div class="companion-helper__panel" id="companion-helper-panel" role="dialog" aria-modal="false"
        aria-labelledby="companion-helper-heading"
        aria-describedby="companion-helper-title companion-helper-lead" hidden>
      <div class="companion-helper__head">
        <span class="companion-helper__avatar" aria-hidden="true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path d="M3 4.5h18v12H9.5L4.8 20V16.5H3v-12Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
            <path d="M7.5 8.5h9M7.5 11.4h6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
          </svg>
        </span>
        <span class="companion-helper__headtext">
          <span class="companion-helper__title" id="companion-helper-heading"><c:out value="${msgLauncher}"/></span>
          <span class="companion-helper__byline">
            <span class="companion-helper__dot" aria-hidden="true"></span>
            <span class="companion-helper__kicker"><c:out value="${msgKicker}"/></span>
            <span class="companion-helper__optional"><c:out value="${msgOptional}"/></span>
          </span>
        </span>
        <button class="companion-helper__close" type="button" data-companion-close
            aria-label="<c:out value='${msgClose}'/>" title="<c:out value='${msgClose}'/>">
          <svg width="16" height="16" viewBox="0 0 14 14" fill="none" aria-hidden="true"><path d="M2 2l10 10M12 2L2 12" stroke="currentColor" stroke-width="1.6"/></svg>
        </button>
      </div>

      <%-- Assistive descriptions; the visible interface is the chat itself. --%>
      <p class="companion-helper__title-lead" id="companion-helper-title"><c:out value="${msgTitle}"/></p>
      <p class="companion-helper__lead" id="companion-helper-lead"><c:out value="${msgLead}"/></p>
      <p class="companion-helper__privacy"><c:out value="${msgPrivacy}"/></p>
      <p class="companion-helper__hint" id="companion-helper-hint"><c:out value="${msgLoadingHint}"/></p>

      <div class="companion-helper__frame">
        <iframe class="companion-helper__iframe"
            data-companion-widget
            src="${fn:escapeXml(helper.widgetUrl)}"
            title="${fn:escapeXml(msgIframeTitle)}"
            loading="lazy"
            referrerpolicy="no-referrer"
            sandbox="allow-scripts allow-forms allow-same-origin allow-modals"
            allow="clipboard-write"
            aria-describedby="companion-helper-hint"></iframe>
      </div>

      <div class="companion-helper__actions">
        <button class="companion-helper__apply" type="button" data-companion-apply aria-describedby="companion-helper-note">
          <span><c:out value="${msgApply}"/></span>
          <svg class="companion-helper__arrow" width="16" height="12" viewBox="0 0 18 14" fill="none" aria-hidden="true"><path d="M1 7h15M10 1l6 6-6 6" stroke="currentColor" stroke-width="1.5"/></svg>
        </button>
        <p class="companion-helper__note" id="companion-helper-note"><c:out value="${msgPrefillNote}"/></p>
        <p class="companion-helper__status" role="status" aria-live="polite" data-companion-status></p>
        <p class="companion-helper__fallback" data-companion-fallback hidden><c:out value="${msgFallback}"/></p>
      </div>
    </div>
  </div>
  <script src="${ctx}/js/companion.js?v=20261112b" defer></script>
</c:if>
