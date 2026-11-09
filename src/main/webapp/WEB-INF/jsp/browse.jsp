<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" session="false" %>
<%@ include file="fragments/taglibs.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="isZh" value="${fn:startsWith(pageLang, 'zh')}"/>
<c:set var="pageTitle"><spring:message code="meta.title.browse"/></c:set>
<c:set var="pageDescription"><spring:message code="meta.description.browse"/></c:set>
<c:url var="clearUrl" value="/browse"/>
<!DOCTYPE html>
<html lang="${pageLang}" class="no-js">
<head>
<%@ include file="fragments/head.jspf" %>
</head>
<body class="page page--browse">
<%@ include file="fragments/topbar.jspf" %>
<main id="main" tabindex="-1">
  <header class="browse-hero">
    <h1 class="display"><spring:message code="browse.title"/></h1>
    <p class="browse-lead"><spring:message code="browse.lead"/></p>
  </header>

  <form class="search-block" action="${ctx}/browse" method="get" role="search">
    <div class="search-form">
      <div class="search-form__field">
        <label for="q"><spring:message code="browse.search.label"/></label>
        <input id="q" name="q" type="search" value="${fn:escapeXml(q)}" placeholder="<spring:message code='browse.search.placeholder'/>" maxlength="120">
      </div>
      <button class="search-form__button" type="submit">
        <span><spring:message code="browse.search.action"/></span>
        <svg width="13" height="13" viewBox="0 0 13 13" fill="none" aria-hidden="true"><path d="M2 11 11 2M4 2h7v7" stroke="currentColor" stroke-width="1.4"/></svg>
      </button>
    </div>

    <div class="filter-bar" role="group" aria-label="<spring:message code='browse.filters'/>">
      <p class="filter">
        <label for="filter-round"><spring:message code="browse.filter.round"/></label>
        <span class="select-wrap">
          <select id="filter-round" name="round" data-auto-submit>
            <option value=""><spring:message code="browse.filter.all"/></option>
            <option value="phone" ${round eq 'phone' ? 'selected' : ''}><spring:message code="round.phone"/></option>
            <option value="technical" ${round eq 'technical' ? 'selected' : ''}><spring:message code="round.technical"/></option>
            <option value="takehome" ${round eq 'takehome' ? 'selected' : ''}><spring:message code="round.takehome"/></option>
            <option value="systemdesign" ${round eq 'systemdesign' ? 'selected' : ''}><spring:message code="round.systemdesign"/></option>
            <option value="behavioral" ${round eq 'behavioral' ? 'selected' : ''}><spring:message code="round.behavioral"/></option>
            <option value="final" ${round eq 'final' ? 'selected' : ''}><spring:message code="round.final"/></option>
            <option value="other" ${round eq 'other' ? 'selected' : ''}><spring:message code="round.other"/></option>
          </select>
          <svg class="select-wrap__arrow" width="10" height="6" viewBox="0 0 11 7" fill="none" aria-hidden="true"><path d="m1 1 4.5 4.5L10 1" stroke="currentColor" stroke-width="1.3"/></svg>
        </span>
      </p>
      <p class="filter">
        <label for="filter-outcome"><spring:message code="browse.filter.outcome"/></label>
        <span class="select-wrap">
          <select id="filter-outcome" name="outcome" data-auto-submit>
            <option value=""><spring:message code="browse.filter.all"/></option>
            <option value="offer" ${outcome eq 'offer' ? 'selected' : ''}><spring:message code="outcome.offer"/></option>
            <option value="rejection" ${outcome eq 'rejection' ? 'selected' : ''}><spring:message code="outcome.rejection"/></option>
            <option value="ghosted" ${outcome eq 'ghosted' ? 'selected' : ''}><spring:message code="outcome.ghosted"/></option>
            <option value="waiting" ${outcome eq 'waiting' ? 'selected' : ''}><spring:message code="outcome.waiting"/></option>
            <option value="withdrew" ${outcome eq 'withdrew' ? 'selected' : ''}><spring:message code="outcome.withdrew"/></option>
          </select>
          <svg class="select-wrap__arrow" width="10" height="6" viewBox="0 0 11 7" fill="none" aria-hidden="true"><path d="m1 1 4.5 4.5L10 1" stroke="currentColor" stroke-width="1.3"/></svg>
        </span>
      </p>
      <p class="filter">
        <label for="filter-mode"><spring:message code="browse.filter.where"/></label>
        <span class="select-wrap">
          <select id="filter-mode" name="workMode" data-auto-submit>
            <option value=""><spring:message code="browse.filter.all"/></option>
            <option value="onsite" ${workMode eq 'onsite' ? 'selected' : ''}><spring:message code="mode.onsite"/></option>
            <option value="hybrid" ${workMode eq 'hybrid' ? 'selected' : ''}><spring:message code="mode.hybrid"/></option>
            <option value="remote" ${workMode eq 'remote' ? 'selected' : ''}><spring:message code="mode.remote"/></option>
          </select>
          <svg class="select-wrap__arrow" width="10" height="6" viewBox="0 0 11 7" fill="none" aria-hidden="true"><path d="m1 1 4.5 4.5L10 1" stroke="currentColor" stroke-width="1.3"/></svg>
        </span>
      </p>
      <p class="filter">
        <label for="filter-sort"><spring:message code="browse.sort"/></label>
        <span class="select-wrap">
          <select id="filter-sort" name="sort" data-auto-submit>
            <option value="recent" ${sort eq 'recent' ? 'selected' : ''}><spring:message code="browse.sort.recent"/></option>
            <option value="helpful" ${sort eq 'helpful' ? 'selected' : ''}><spring:message code="browse.sort.helpful"/></option>
          </select>
          <svg class="select-wrap__arrow" width="10" height="6" viewBox="0 0 11 7" fill="none" aria-hidden="true"><path d="m1 1 4.5 4.5L10 1" stroke="currentColor" stroke-width="1.3"/></svg>
        </span>
      </p>
      <div class="filter-bar__actions">
        <button class="filter-bar__apply" type="submit"><spring:message code="browse.filter.apply"/></button>
        <a class="filter-bar__clear" href="${clearUrl}"><spring:message code="browse.filter.clear"/></a>
      </div>
    </div>
  </form>

  <div class="results-head">
    <p class="result-count" role="status">
      <c:choose>
        <c:when test="${total eq 1}"><spring:message code="browse.result.count.one" arguments="${total}"/></c:when>
        <c:otherwise><spring:message code="browse.result.count" arguments="${total}"/></c:otherwise>
      </c:choose>
    </p>
  </div>

  <c:choose>
    <c:when test="${not empty results}">
      <ul class="results" role="list" aria-label="<spring:message code='browse.results.label'/>">
        <c:forEach items="${results}" var="note" varStatus="status">
          <li>
            <a class="note-row" href="${ctx}/reports/${note.id}">
              <span class="note-row__index" aria-hidden="true"><fmt:formatNumber value="${(page - 1) * 10 + status.index + 1}" pattern="00"/></span>
              <span class="note-row__content">
                <span class="note-row__top">
                  <span class="note-row__company"><c:out value="${note.company}"/></span>
                  <span class="note-row__role"><c:out value="${note.role}"/></span>
                </span>
                <span class="note-row__meta">
                  <span class="tag"><spring:message code="round.${note.round}"/></span>
                  <c:if test="${not empty note.workMode}"><span aria-hidden="true">·</span><span><spring:message code="mode.${note.workMode}"/></span></c:if>
                  <c:if test="${not empty note.location}"><span aria-hidden="true">·</span><span><c:out value="${note.location}"/></span></c:if>
                  <span aria-hidden="true">·</span>
                  <span><spring:message code="outcome.${note.outcome}"/></span>
                  <span aria-hidden="true">·</span>
                  <span><c:out value="${note.createdAtLabel}"/></span>
                </span>
                <span class="note-row__excerpt"><c:out value="${note.excerpt}"/></span>
              </span>
              <span class="note-row__side">
                <c:if test="${note.helpful gt 0}">
                  <span><spring:message code="browse.card.useful" arguments="${note.helpful}"/></span>
                </c:if>
                <span class="note-row__arrow" aria-hidden="true">↗</span>
              </span>
            </a>
          </li>
        </c:forEach>
      </ul>

      <c:if test="${totalPages gt 1}">
        <nav class="pagination" aria-label="<spring:message code='browse.results.label'/>">
          <div class="pagination__pages">
            <c:if test="${page gt 1}">
              <c:url var="prevUrl" value="/browse">
                <c:if test="${not empty q}"><c:param name="q" value="${q}"/></c:if>
                <c:if test="${not empty round}"><c:param name="round" value="${round}"/></c:if>
                <c:if test="${not empty outcome}"><c:param name="outcome" value="${outcome}"/></c:if>
                <c:if test="${not empty workMode}"><c:param name="workMode" value="${workMode}"/></c:if>
                <c:if test="${sort ne 'recent'}"><c:param name="sort" value="${sort}"/></c:if>
                <c:param name="page" value="${page - 1}"/>
              </c:url>
              <a href="${prevUrl}"><span aria-hidden="true">←</span><spring:message code="browse.page.prev"/></a>
            </c:if>
            <c:if test="${page lt totalPages}">
              <c:url var="nextUrl" value="/browse">
                <c:if test="${not empty q}"><c:param name="q" value="${q}"/></c:if>
                <c:if test="${not empty round}"><c:param name="round" value="${round}"/></c:if>
                <c:if test="${not empty outcome}"><c:param name="outcome" value="${outcome}"/></c:if>
                <c:if test="${not empty workMode}"><c:param name="workMode" value="${workMode}"/></c:if>
                <c:if test="${sort ne 'recent'}"><c:param name="sort" value="${sort}"/></c:if>
                <c:param name="page" value="${page + 1}"/>
              </c:url>
              <a href="${nextUrl}"><spring:message code="browse.page.next"/><span aria-hidden="true">→</span></a>
            </c:if>
          </div>
          <p class="pagination__status"><spring:message code="browse.page.status" arguments="${page},${totalPages}"/></p>
        </nav>
      </c:if>
    </c:when>
    <c:otherwise>
      <section class="empty-state" aria-labelledby="empty-title">
        <h2 class="empty-state__title" id="empty-title"><spring:message code="browse.empty.title"/></h2>
        <p class="empty-state__body"><spring:message code="browse.empty.body"/></p>
        <div class="empty-state__actions">
          <a href="${ctx}/"><spring:message code="browse.empty.post"/></a>
          <a href="${clearUrl}"><spring:message code="browse.empty.clear"/></a>
        </div>
      </section>
    </c:otherwise>
  </c:choose>
</main>
<%@ include file="fragments/footer.jspf" %>
</body>
</html>
