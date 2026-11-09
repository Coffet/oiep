<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" session="false" %>
<%@ include file="fragments/taglibs.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="isZh" value="${fn:startsWith(pageLang, 'zh')}"/>
<c:set var="pageTitle">${report.company} — OIEP</c:set>
<c:set var="pageDescription">${report.excerpt}</c:set>
<!DOCTYPE html>
<html lang="${pageLang}" class="no-js">
<head>
<%@ include file="fragments/head.jspf" %>
</head>
<body class="page page--report">
<%@ include file="fragments/topbar.jspf" %>
<main id="main" tabindex="-1">
  <div class="note-page">
    <a class="back-link" href="${ctx}/browse">
      <span class="back-link__arrow" aria-hidden="true">←</span>
      <spring:message code="report.back"/>
    </a>

    <c:if test="${justPosted}">
      <section class="posted-banner" role="status" aria-labelledby="posted-title">
        <div>
          <h2 class="posted-banner__title" id="posted-title"><spring:message code="report.posted.title"/></h2>
          <p class="posted-banner__body"><spring:message code="report.posted.body"/></p>
        </div>
        <a href="${ctx}/"><spring:message code="report.posted.again"/><span aria-hidden="true">→</span></a>
      </section>
    </c:if>

    <header class="note-header">
      <p class="note-kicker">
        <span class="note-kicker__id"><spring:message code="report.kicker"/><span class="note-kicker__num"><fmt:formatNumber value="${report.id}" pattern="000"/></span></span>
        <span class="note-kicker__posted"><spring:message code="report.meta.posted"/><span class="note-kicker__date"><c:out value="${report.createdAtLabel}"/></span></span>
      </p>
      <h1 class="note-title"><c:out value="${report.company}"/></h1>
      <p class="note-role"><c:out value="${report.role}"/></p>
    </header>

    <div class="note-layout">
      <article class="note-story">
        <h2 class="sr-only"><spring:message code="report.story.heading"/></h2>
        <p class="note-story__text"><c:out value="${report.story}"/></p>
        <p class="note-story__signoff"><spring:message code="report.anonymous"/></p>
      </article>

      <aside class="note-aside" aria-labelledby="note-details-title">
        <h2 class="sr-only" id="note-details-title"><spring:message code="report.meta.round"/></h2>
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
                <c:when test="${not empty report.workMode}">
                  <spring:message code="mode.${report.workMode}"/>
                </c:when>
                <c:when test="${not empty report.location}">
                  <c:out value="${report.location}"/>
                </c:when>
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
        </dl>

        <section class="useful" id="useful" aria-labelledby="useful-title">
          <h2 class="sr-only" id="useful-title"><spring:message code="report.helpful.question"/></h2>
          <c:choose>
            <c:when test="${alreadyHelpful}">
              <p class="useful__thanks"><spring:message code="report.helpful.thanks"/></p>
            </c:when>
            <c:otherwise>
              <p class="useful__question"><spring:message code="report.helpful.question"/></p>
              <form action="${ctx}/reports/${report.id}/helpful" method="post">
                <button class="useful__button" type="submit">
                  <span><spring:message code="report.helpful.action"/></span>
                  <span aria-hidden="true">↗</span>
                </button>
              </form>
            </c:otherwise>
          </c:choose>
          <p class="useful__count">
            <c:choose>
              <c:when test="${report.helpful eq 1}"><spring:message code="report.helpful.count.one" arguments="${report.helpful}"/></c:when>
              <c:otherwise><spring:message code="report.helpful.count" arguments="${report.helpful}"/></c:otherwise>
            </c:choose>
          </p>
        </section>
      </aside>
    </div>
  </div>
</main>
<%@ include file="fragments/footer.jspf" %>
</body>
</html>
