<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" session="false" %>
<%@ include file="../fragments/taglibs.jspf" %>
<%--
  Optional AI companion — user helper on the public debrief page.

  Rendered by the generic pageExtensions loop in index.jsp only while the companion user
  helper is enabled and its widget URL is valid: no bean = no include = no markup. This
  fragment is a self-contained block: it links its own stylesheet, escapes every dynamic
  value, uses no inline style/script (CSP is hash-based) and never submits anything.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="helper" value="${companionUserHelper}"/>
<c:if test="${not empty helper and helper.available}">
  <spring:message code="companion.helper.kicker" var="msgKicker"/>
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
  <section class="companion-helper" aria-labelledby="companion-helper-heading"
      data-companion-helper
      data-widget-origin="${fn:escapeXml(helper.widgetOrigin)}"
      data-msg-checking="${fn:escapeXml(msgChecking)}"
      data-msg-applied="${fn:escapeXml(msgApplied)}"
      data-msg-applied-count="${fn:escapeXml(msgAppliedCount)}"
      data-msg-unchanged="${fn:escapeXml(msgUnchanged)}"
      data-msg-no-match="${fn:escapeXml(msgNoMatch)}"
      data-msg-skipped="${fn:escapeXml(msgSkipped)}"
      data-msg-not-ready="${fn:escapeXml(msgNotReady)}"
      data-msg-unavailable="${fn:escapeXml(msgUnavailable)}">
    <p class="eyebrow eyebrow--quiet companion-helper__kicker">
      <span class="eyebrow__dot" aria-hidden="true"></span><c:out value="${msgKicker}"/>
    </p>
    <div class="companion-helper__grid">
      <div class="companion-helper__intro">
        <div class="companion-helper__title-row">
          <h2 class="companion-helper__title" id="companion-helper-heading"><c:out value="${msgTitle}"/></h2>
          <span class="companion-helper__optional"><c:out value="${msgOptional}"/></span>
        </div>
        <p class="companion-helper__lead"><c:out value="${msgLead}"/></p>
        <p class="companion-helper__privacy">
          <svg class="companion-helper__lock" width="13" height="15" viewBox="0 0 15 17" fill="none" aria-hidden="true"><rect x="1" y="7" width="13" height="9" stroke="currentColor"/><path d="M4 7V4.5a3.5 3.5 0 0 1 7 0V7" stroke="currentColor"/></svg>
          <span><c:out value="${msgPrivacy}"/></span>
        </p>
      </div>
      <div class="companion-helper__panel">
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
          <p class="companion-helper__note" id="companion-helper-note"><c:out value="${msgPrefillNote}"/></p>
        </div>
        <p class="companion-helper__status" id="companion-helper-status" role="status" aria-live="polite" data-companion-status></p>
        <p class="companion-helper__fallback" data-companion-fallback hidden><c:out value="${msgFallback}"/></p>
      </div>
    </div>
  </section>
  <script src="${ctx}/js/companion.js" defer></script>
</c:if>
