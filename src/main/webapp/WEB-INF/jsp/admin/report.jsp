<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" session="false" %>
<%@ include file="../fragments/taglibs.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="isZh" value="${fn:startsWith(pageLang, 'zh')}"/>
<c:set var="pageTitle">${report.company} — <spring:message code="admin.dashboard.title"/> — OIEP</c:set>
<c:set var="pageDescription" value="${report.excerpt}"/>
<c:set var="adminCss" value="true"/>
<!DOCTYPE html>
<html lang="${pageLang}" class="no-js">
<head>
<%@ include file="../fragments/head.jspf" %>
</head>
<body class="page page--admin">
<%@ include file="../fragments/admin-topbar.jspf" %>
<main id="main" class="admin-main" tabindex="-1">
  <a class="back-link" href="${ctx}/admin">
    <span class="back-link__arrow" aria-hidden="true">←</span>
    <spring:message code="admin.detail.back"/>
  </a>

  <c:if test="${not empty adminNotice}">
    <p class="admin-flash" role="status"><spring:message code="${adminNotice}"/></p>
  </c:if>

  <header class="admin-note-header">
    <p class="admin-kicker">
      <span><spring:message code="admin.detail.eyebrow" arguments="${report.id}"/></span>
      <span>
        <c:choose>
          <c:when test="${report.status eq 'hidden'}"><span class="admin-status admin-status--hidden"><spring:message code="admin.status.hidden"/></span></c:when>
          <c:when test="${report.status eq 'published'}"><span class="admin-status admin-status--published"><spring:message code="admin.status.published"/></span></c:when>
          <c:otherwise><span class="admin-status admin-status--unknown"><spring:message code="admin.status.unknown"/></span></c:otherwise>
        </c:choose>
      </span>
    </p>
    <h1 class="display"><c:out value="${report.company}"/></h1>
    <p class="admin-note-role"><c:out value="${report.role}"/></p>
  </header>

  <c:if test="${report.status eq 'hidden'}">
    <p class="admin-hidden-note" role="status"><spring:message code="admin.detail.hiddenNotice"/></p>
  </c:if>

  <div class="admin-detail-grid">
    <article class="admin-detail-story">
      <h2 class="admin-detail__heading"><spring:message code="report.story.heading"/></h2>
      <p class="note-story__text"><c:out value="${report.story}"/></p>
      <p class="note-story__signoff"><spring:message code="report.anonymous"/></p>
    </article>

    <aside class="admin-detail-aside" aria-labelledby="admin-detail-title">
      <h2 class="admin-detail__heading" id="admin-detail-title"><spring:message code="admin.detail.actions"/></h2>
      <dl class="note-meta">
        <div class="note-meta__row">
          <dt><spring:message code="report.meta.round"/></dt>
          <dd><spring:message code="round.${report.round}"/></dd>
        </div>
        <div class="note-meta__row">
          <dt><spring:message code="report.meta.where"/></dt>
          <dd>
            <c:choose>
              <c:when test="${not empty report.workMode and not empty report.location}">
                <span class="note-meta__inline">
                  <span><spring:message code="mode.${report.workMode}"/></span>
                  <span class="note-meta__dot" aria-hidden="true">·</span>
                  <span><c:out value="${report.location}"/></span>
                </span>
              </c:when>
              <c:when test="${not empty report.workMode}"><spring:message code="mode.${report.workMode}"/></c:when>
              <c:when test="${not empty report.location}"><c:out value="${report.location}"/></c:when>
              <c:otherwise><spring:message code="report.meta.locationUnsaid"/></c:otherwise>
            </c:choose>
          </dd>
        </div>
        <div class="note-meta__row">
          <dt><spring:message code="report.meta.outcome"/></dt>
          <dd><spring:message code="outcome.${report.outcome}"/></dd>
        </div>
        <c:if test="${not empty report.heardBack}">
          <div class="note-meta__row">
            <dt><spring:message code="report.meta.heard"/></dt>
            <dd><spring:message code="heardback.${report.heardBack}"/></dd>
          </div>
        </c:if>
        <div class="note-meta__row">
          <dt><spring:message code="report.meta.questions"/></dt>
          <dd>
            <c:choose>
              <c:when test="${not empty report.questionTypes}">
                <ul class="note-tags" role="list">
                  <c:forEach items="${report.questionTypes}" var="question">
                    <li><spring:message code="question.${question}"/></li>
                  </c:forEach>
                </ul>
              </c:when>
              <c:otherwise><spring:message code="report.meta.none"/></c:otherwise>
            </c:choose>
          </dd>
        </div>
        <c:if test="${not empty report.vibe}">
          <div class="note-meta__row">
            <dt><spring:message code="report.meta.vibe"/></dt>
            <dd><spring:message code="vibe.${report.vibe}"/></dd>
          </div>
        </c:if>
        <c:if test="${not empty report.salary}">
          <div class="note-meta__row">
            <dt><spring:message code="report.meta.salary"/></dt>
            <dd><c:out value="${report.salary}"/></dd>
          </div>
        </c:if>
        <div class="note-meta__row">
          <dt><spring:message code="report.meta.posted"/></dt>
          <dd><c:out value="${report.createdAtLabel}"/></dd>
        </div>
        <div class="note-meta__row">
          <dt><spring:message code="admin.table.useful"/></dt>
          <dd><fmt:formatNumber value="${report.helpful}" pattern="#,##0"/></dd>
        </div>
      </dl>

      <div class="admin-actions">
        <c:if test="${report.status eq 'published'}">
          <a class="admin-action-link" href="${ctx}/reports/${report.id}"><spring:message code="admin.detail.public"/></a>
        </c:if>
        <c:choose>
          <c:when test="${report.status eq 'hidden'}">
            <form action="${ctx}/admin/reports/${report.id}/restore" method="post">
              <input type="hidden" name="_csrf" value="<c:out value='${adminCsrfToken}'/>">
              <input type="hidden" name="returnTo" value="<c:out value='${adminReturnUrl}'/>">
              <button class="admin-btn admin-btn--primary admin-btn--small" type="submit"><spring:message code="admin.action.restore"/></button>
            </form>
          </c:when>
          <c:otherwise>
            <form action="${ctx}/admin/reports/${report.id}/hide" method="post">
              <input type="hidden" name="_csrf" value="<c:out value='${adminCsrfToken}'/>">
              <input type="hidden" name="returnTo" value="<c:out value='${adminReturnUrl}'/>">
              <button class="admin-btn admin-btn--small" type="submit"><spring:message code="admin.action.hide"/></button>
            </form>
          </c:otherwise>
        </c:choose>
        <a class="admin-delete-link" href="${ctx}/admin/reports/${report.id}/delete"><spring:message code="admin.detail.delete"/></a>
      </div>
    </aside>
  </div>
</main>
<%@ include file="../fragments/footer.jspf" %>
</body>
</html>
