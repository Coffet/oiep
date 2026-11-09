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
<body class="page page--admin page--admin-login">
<%@ include file="../fragments/admin-topbar.jspf" %>
<main id="main" class="admin-main admin-main--login" tabindex="-1">
  <div class="admin-login">
    <h1 class="display"><spring:message code="admin.login.title"/></h1>
    <p class="admin-lead"><spring:message code="admin.login.lead"/></p>

    <c:if test="${loggedOut}">
      <p class="admin-flash" role="status"><spring:message code="admin.login.loggedOut"/></p>
    </c:if>
    <c:if test="${not adminConfigured}">
      <div class="admin-alert" role="alert"><spring:message code="admin.login.notConfigured"/></div>
    </c:if>
    <c:if test="${not empty loginError}">
      <div class="admin-alert" role="alert"><spring:message code="${loginError}"/></div>
    </c:if>

    <c:if test="${adminConfigured}">
      <form class="admin-login__form" action="${ctx}/admin/login" method="post">
        <input type="hidden" name="_csrf" value="<c:out value='${adminCsrfToken}'/>">
        <p class="admin-field">
          <label for="admin-username"><spring:message code="admin.login.username"/></label>
          <input id="admin-username" name="username" type="text" autocomplete="username" required autofocus>
        </p>
        <p class="admin-field">
          <label for="admin-password"><spring:message code="admin.login.password"/></label>
          <input id="admin-password" name="password" type="password" autocomplete="current-password" required>
        </p>
        <button class="admin-btn admin-btn--primary" type="submit"><spring:message code="admin.login.submit"/></button>
      </form>
    </c:if>

    <a class="admin-back" href="${ctx}/"><spring:message code="admin.login.back"/></a>
  </div>
</main>
<%@ include file="../fragments/footer.jspf" %>
</body>
</html>
