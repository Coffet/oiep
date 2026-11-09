<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" session="false" %>
<%@ include file="../fragments/taglibs.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="isZh" value="${fn:startsWith(pageLang, 'zh')}"/>
<c:set var="pageTitle"><spring:message code="meta.title.admin"/></c:set>
<c:set var="pageDescription"><spring:message code="meta.description.admin"/></c:set>
<c:set var="adminCss" value="true"/>
<!DOCTYPE html>
<html lang="${pageLang}" class="no-js">
<head>
<%@ include file="../fragments/head.jspf" %>
</head>
<body class="page page--admin">
<%@ include file="../fragments/admin-topbar.jspf" %>
<main id="main" class="admin-main" tabindex="-1">
  <header class="admin-hero">
    <p class="eyebrow"><span class="eyebrow__dot" aria-hidden="true"></span><spring:message code="admin.dashboard.eyebrow"/></p>
    <h1 class="display"><spring:message code="admin.dashboard.title"/></h1>
    <p class="admin-lead"><spring:message code="admin.dashboard.lead"/></p>
  </header>

  <c:if test="${not empty adminNotice}">
    <p class="admin-flash" role="status"><spring:message code="${adminNotice}"/></p>
  </c:if>

  <section class="admin-stats" aria-label="<spring:message code='admin.dashboard.title'/>">
    <div class="admin-stat">
      <span class="admin-stat__num"><fmt:formatNumber value="${totalNotes}" pattern="#,##0"/></span>
      <span class="admin-stat__label"><spring:message code="admin.stats.total"/></span>
    </div>
    <div class="admin-stat">
      <span class="admin-stat__num"><fmt:formatNumber value="${publishedNotes}" pattern="#,##0"/></span>
      <span class="admin-stat__label"><spring:message code="admin.stats.published"/></span>
    </div>
    <div class="admin-stat">
      <span class="admin-stat__num"><fmt:formatNumber value="${hiddenNotes}" pattern="#,##0"/></span>
      <span class="admin-stat__label"><spring:message code="admin.stats.hidden"/></span>
    </div>
    <div class="admin-stat">
      <span class="admin-stat__num"><fmt:formatNumber value="${helpfulTotal}" pattern="#,##0"/></span>
      <span class="admin-stat__label"><spring:message code="admin.stats.helpful"/></span>
    </div>
    <div class="admin-stat">
      <span class="admin-stat__num"><fmt:formatNumber value="${recentNotes}" pattern="#,##0"/></span>
      <span class="admin-stat__label"><spring:message code="admin.stats.recent"/></span>
    </div>
  </section>

  <section class="admin-board" aria-labelledby="admin-board-title">
    <div class="admin-board__head">
      <h2 class="admin-board__title" id="admin-board-title"><spring:message code="admin.board.title"/></h2>
      <form class="admin-toolbar" action="${ctx}/admin" method="get" role="search">
        <div class="admin-toolbar__search">
          <label class="sr-only" for="admin-q"><spring:message code="admin.search.label"/></label>
          <input id="admin-q" name="q" type="search" value="${fn:escapeXml(q)}" placeholder="<spring:message code='admin.search.placeholder'/>" maxlength="120">
        </div>
        <div class="admin-toolbar__status">
          <label class="sr-only" for="admin-status"><spring:message code="admin.filter.status"/></label>
          <select id="admin-status" name="status">
            <option value="" ${empty status ? 'selected' : ''}><spring:message code="admin.filter.all"/></option>
            <option value="published" ${status eq 'published' ? 'selected' : ''}><spring:message code="admin.filter.published"/></option>
            <option value="hidden" ${status eq 'hidden' ? 'selected' : ''}><spring:message code="admin.filter.hidden"/></option>
          </select>
        </div>
        <button class="admin-btn admin-btn--small" type="submit"><spring:message code="admin.search.action"/></button>
        <c:if test="${not empty q or not empty status}">
          <a class="admin-clear" href="${ctx}/admin"><spring:message code="admin.filter.clear"/></a>
        </c:if>
      </form>
    </div>

    <p class="admin-result-count" role="status">
      <c:choose>
        <c:when test="${total eq 1}"><spring:message code="admin.results.count.one" arguments="${total}"/></c:when>
        <c:otherwise><spring:message code="admin.results.count" arguments="${total}"/></c:otherwise>
      </c:choose>
    </p>

    <div class="admin-table-wrap">
      <table class="admin-table">
        <caption class="sr-only"><spring:message code="admin.board.title"/></caption>
        <thead>
          <tr>
            <th scope="col"><spring:message code="admin.table.id"/></th>
            <th scope="col"><spring:message code="admin.table.note"/></th>
            <th scope="col"><spring:message code="admin.table.interview"/></th>
            <th scope="col"><spring:message code="admin.table.date"/></th>
            <th scope="col"><spring:message code="admin.table.useful"/></th>
            <th scope="col"><spring:message code="admin.table.status"/></th>
            <th scope="col"><spring:message code="admin.table.actions"/></th>
          </tr>
        </thead>
        <tbody>
          <c:choose>
            <c:when test="${not empty reports}">
              <c:forEach items="${reports}" var="note">
                <tr>
                  <td class="admin-table__id" data-label="<spring:message code='admin.table.id'/>"><c:out value="${note.id}"/></td>
                  <td data-label="<spring:message code='admin.table.note'/>">
                    <a class="admin-note-link" href="${ctx}/admin/reports/${note.id}">
                      <span class="admin-note-link__company"><c:out value="${note.company}"/></span>
                      <span class="admin-note-link__role"><c:out value="${note.role}"/></span>
                    </a>
                  </td>
                  <td class="admin-table__interview" data-label="<spring:message code='admin.table.interview'/>">
                    <span><spring:message code="round.${note.round}"/></span>
                    <span><spring:message code="outcome.${note.outcome}"/></span>
                  </td>
                  <td data-label="<spring:message code='admin.table.date'/>"><c:out value="${note.createdAtLabel}"/></td>
                  <td data-label="<spring:message code='admin.table.useful'/>"><fmt:formatNumber value="${note.helpful}" pattern="#,##0"/></td>
                  <td data-label="<spring:message code='admin.table.status'/>">
                    <c:choose>
                      <c:when test="${note.status eq 'hidden'}"><span class="admin-status admin-status--hidden"><spring:message code="admin.status.hidden"/></span></c:when>
                      <c:when test="${note.status eq 'published'}"><span class="admin-status admin-status--published"><spring:message code="admin.status.published"/></span></c:when>
                      <c:otherwise><span class="admin-status admin-status--unknown"><spring:message code="admin.status.unknown"/></span></c:otherwise>
                    </c:choose>
                  </td>
                  <td class="admin-table__actions" data-label="<spring:message code='admin.table.actions'/>">
                    <a class="admin-action" href="${ctx}/admin/reports/${note.id}"><spring:message code="admin.action.manage"/></a>
                    <c:choose>
                      <c:when test="${note.status eq 'hidden'}">
                        <form class="admin-action-form" action="${ctx}/admin/reports/${note.id}/restore" method="post">
                          <input type="hidden" name="_csrf" value="<c:out value='${adminCsrfToken}'/>">
                          <input type="hidden" name="returnTo" value="<c:out value='${adminReturnUrl}'/>">
                          <button class="admin-action admin-action--quiet" type="submit"><spring:message code="admin.action.restore"/></button>
                        </form>
                      </c:when>
                      <c:otherwise>
                        <form class="admin-action-form" action="${ctx}/admin/reports/${note.id}/hide" method="post">
                          <input type="hidden" name="_csrf" value="<c:out value='${adminCsrfToken}'/>">
                          <input type="hidden" name="returnTo" value="<c:out value='${adminReturnUrl}'/>">
                          <button class="admin-action admin-action--danger" type="submit"><spring:message code="admin.action.hide"/></button>
                        </form>
                      </c:otherwise>
                    </c:choose>
                  </td>
                </tr>
              </c:forEach>
            </c:when>
            <c:otherwise>
              <tr>
                <td class="admin-empty" colspan="7">
                  <strong><spring:message code="admin.empty.title"/></strong>
                  <span><spring:message code="admin.empty.body"/></span>
                  <a href="${ctx}/admin"><spring:message code="admin.empty.clear"/></a>
                </td>
              </tr>
            </c:otherwise>
          </c:choose>
        </tbody>
      </table>
    </div>

    <c:if test="${totalPages gt 1}">
      <nav class="admin-pagination" aria-label="<spring:message code='admin.board.title'/>">
        <div class="admin-pagination__pages">
          <c:if test="${page gt 1}">
            <c:url var="prevUrl" value="/admin">
              <c:if test="${not empty q}"><c:param name="q" value="${q}"/></c:if>
              <c:if test="${not empty status}"><c:param name="status" value="${status}"/></c:if>
              <c:param name="page" value="${page - 1}"/>
            </c:url>
            <a href="${prevUrl}"><span aria-hidden="true">←</span><spring:message code="admin.page.prev"/></a>
          </c:if>
          <c:if test="${page lt totalPages}">
            <c:url var="nextUrl" value="/admin">
              <c:if test="${not empty q}"><c:param name="q" value="${q}"/></c:if>
              <c:if test="${not empty status}"><c:param name="status" value="${status}"/></c:if>
              <c:param name="page" value="${page + 1}"/>
            </c:url>
            <a href="${nextUrl}"><spring:message code="admin.page.next"/><span aria-hidden="true">→</span></a>
          </c:if>
        </div>
        <p><spring:message code="admin.page.status" arguments="${page},${totalPages}"/></p>
      </nav>
    </c:if>
  </section>
</main>
<%@ include file="../fragments/footer.jspf" %>
</body>
</html>
