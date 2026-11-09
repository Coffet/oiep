<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" session="false" %>
<%@ include file="fragments/taglibs.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="isZh" value="${fn:startsWith(pageLang, 'zh')}"/>
<c:set var="pageTitle"><spring:message code="meta.title.about"/></c:set>
<c:set var="pageDescription"><spring:message code="meta.description.about"/></c:set>
<!DOCTYPE html>
<html lang="${pageLang}" class="no-js">
<head>
<%@ include file="fragments/head.jspf" %>
</head>
<body class="page page--about">
<%@ include file="fragments/topbar.jspf" %>
<main id="main" tabindex="-1">
  <div class="about-page">
    <header>
      <h1 class="display"><spring:message code="about.title"/></h1>
      <p class="about-lead"><spring:message code="about.lead"/></p>
    </header>

    <blockquote class="about-quote">
      <p>
        <span class="sr-only"><spring:message code="about.quote"/></span>
        <span aria-hidden="true">
          <span class="about-quote__line"><spring:message code="about.quote.line1"/></span>
          <span class="about-quote__line"><spring:message code="about.quote.line2"/></span>
        </span>
      </p>
    </blockquote>
    <p class="about-values"><spring:message code="about.values"/></p>

    <section class="about-section" aria-labelledby="about-problem">
      <h2 class="about-section__label" id="about-problem"><spring:message code="about.section.problem"/></h2>
      <div class="about-section__body">
        <p><spring:message code="about.problem.p1"/></p>
        <p><spring:message code="about.problem.p2"/></p>
      </div>
    </section>

    <section class="about-section" aria-labelledby="about-is">
      <h2 class="about-section__label" id="about-is"><spring:message code="about.section.is"/></h2>
      <div class="about-section__body">
        <p><spring:message code="about.is.p1"/></p>
        <p><spring:message code="about.is.p2"/></p>
      </div>
    </section>

    <section class="about-section" aria-labelledby="about-how">
      <h2 class="about-section__label" id="about-how"><spring:message code="about.section.how"/></h2>
      <div class="about-section__body">
        <p><spring:message code="about.how.p1"/></p>
        <p><spring:message code="about.how.p2"/></p>
      </div>
    </section>

    <section class="about-section" aria-labelledby="about-stack">
      <h2 class="about-section__label" id="about-stack"><spring:message code="about.section.stack"/></h2>
      <div class="about-section__body">
        <p><spring:message code="about.stack.intro"/></p>
        <dl class="about-stack">
          <div class="about-stack__row">
            <dt><spring:message code="about.stack.label.backend"/></dt>
            <dd><spring:message code="about.stack.backend"/></dd>
          </div>
          <div class="about-stack__row">
            <dt><spring:message code="about.stack.label.frontend"/></dt>
            <dd><spring:message code="about.stack.frontend"/></dd>
          </div>
          <div class="about-stack__row">
            <dt><spring:message code="about.stack.label.storage"/></dt>
            <dd><spring:message code="about.stack.data"/></dd>
          </div>
          <div class="about-stack__row">
            <dt><spring:message code="about.stack.label.deploy"/></dt>
            <dd><spring:message code="about.stack.deploy"/></dd>
          </div>
        </dl>
      </div>
    </section>

    <section class="about-section" aria-labelledby="about-roadmap">
      <h2 class="about-section__label" id="about-roadmap"><spring:message code="about.section.roadmap"/></h2>
      <div class="about-section__body">
        <ul class="about-list" role="list">
          <li><spring:message code="about.roadmap.submission"/></li>
          <li><spring:message code="about.roadmap.tagging"/></li>
          <li><spring:message code="about.roadmap.rating"/></li>
          <li><spring:message code="about.roadmap.moderation"/></li>
          <li><spring:message code="about.roadmap.export"/></li>
        </ul>
      </div>
    </section>

    <section class="about-section" aria-labelledby="about-open">
      <h2 class="about-section__label" id="about-open"><spring:message code="about.section.open"/></h2>
      <div class="about-section__body">
        <p><spring:message code="about.open.p1"/></p>
        <p><spring:message code="about.open.p2"/></p>
      </div>
    </section>

    <div class="about-cta">
      <a class="btn btn--primary" href="${ctx}/">
        <span><spring:message code="about.cta"/></span>
        <svg class="btn__arrow" width="18" height="14" viewBox="0 0 18 14" fill="none" aria-hidden="true"><path d="M1 7h15M10 1l6 6-6 6" stroke="currentColor" stroke-width="1.5"/></svg>
      </a>
      <a href="${ctx}/browse"><spring:message code="about.browse"/></a>
    </div>
  </div>
</main>
<%@ include file="fragments/footer.jspf" %>
</body>
</html>
