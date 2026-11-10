<%-- Optional AI companion component: operator-only link rendered in the shared admin top bar
     through the generic pageExtensions loop (page slot "admin-nav"). Standalone fragment: its own
     taglib include and context path, no dependence on the including page's directives. --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" session="false" %>
<%@ include file="../fragments/taglibs.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<a class="topbar__browse" href="${ctx}/admin/assistant"<c:if test="${currentUrl eq '/admin/assistant'}"> aria-current="page"</c:if>><spring:message code="companion.admin.nav"/></a>
