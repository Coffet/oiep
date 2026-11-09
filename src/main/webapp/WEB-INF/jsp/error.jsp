<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" session="false" %>
<%@ include file="fragments/taglibs.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="isZh" value="${fn:startsWith(pageLang, 'zh')}"/>
<c:set var="pageTitle"><spring:message code="meta.title.error"/></c:set>
<c:set var="pageDescription"><spring:message code="meta.description.error"/></c:set>
<c:set var="statusCode" value="${empty status ? 404 : status}"/>
<!DOCTYPE html>
<html lang="${pageLang}" class="no-js">
<head>
<%@ include file="fragments/head.jspf" %>
</head>
<body class="page page--error">
<%@ include file="fragments/topbar.jspf" %>
<main id="main" tabindex="-1">
  <div class="error-page">
    <p class="error-code"><spring:message code="error.status" arguments="${statusCode}"/></p>
    <h1 class="display"><spring:message code="error.title"/></h1>
    <p class="error-body"><spring:message code="error.body"/></p>
    <div class="error-actions">
      <a href="${ctx}/"><spring:message code="error.home"/></a>
      <a href="${ctx}/browse"><spring:message code="error.browse"/></a>
    </div>
  </div>
</main>
<%@ include file="fragments/footer.jspf" %>
</body>
</html>
