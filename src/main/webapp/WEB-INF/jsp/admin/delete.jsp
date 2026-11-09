<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" session="false" %>
<%@ include file="../fragments/taglibs.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="isZh" value="${fn:startsWith(pageLang, 'zh')}"/>
<c:set var="pageTitle"><spring:message code="admin.delete.title"/> — OIEP</c:set>
<c:set var="pageDescription"><spring:message code="admin.delete.lead"/></c:set>
<c:set var="adminCss" value="true"/>
<!DOCTYPE html>
<html lang="${pageLang}" class="no-js">
<head>
<%@ include file="../fragments/head.jspf" %>
</head>
<body class="page page--admin page--admin-delete">
<%@ include file="../fragments/admin-topbar.jspf" %>
<main id="main" class="admin-main admin-main--narrow" tabindex="-1">
  <a class="back-link" href="${ctx}/admin/reports/${report.id}">
    <span class="back-link__arrow" aria-hidden="true">←</span>
    <spring:message code="admin.delete.back"/>
  </a>

  <header class="admin-hero admin-hero--delete">
    <p class="eyebrow"><span class="eyebrow__dot" aria-hidden="true"></span><spring:message code="admin.delete.eyebrow"/></p>
    <h1 class="display"><spring:message code="admin.delete.title"/></h1>
    <p class="admin-lead"><spring:message code="admin.delete.lead"/></p>
  </header>

  <div class="admin-delete-note">
    <p class="admin-delete-note__id"><spring:message code="admin.detail.eyebrow" arguments="${report.id}"/></p>
    <p class="admin-delete-note__company"><c:out value="${report.company}"/></p>
    <p class="admin-delete-note__role"><c:out value="${report.role}"/></p>
  </div>

  <div class="admin-delete-actions">
    <form action="${ctx}/admin/reports/${report.id}/delete" method="post">
      <input type="hidden" name="_csrf" value="<c:out value='${adminCsrfToken}'/>">
      <button class="admin-btn admin-btn--danger" type="submit"><spring:message code="admin.delete.confirm"/></button>
    </form>
    <a class="admin-clear" href="${ctx}/admin/reports/${report.id}"><spring:message code="admin.delete.cancel"/></a>
  </div>
</main>
<%@ include file="../fragments/footer.jspf" %>
</body>
</html>
