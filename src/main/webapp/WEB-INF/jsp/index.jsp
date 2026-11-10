<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" session="false" %>
<%@ include file="fragments/taglibs.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="isZh" value="${fn:startsWith(pageLang, 'zh')}"/>
<c:set var="pageTitle"><spring:message code="meta.title.index"/></c:set>
<c:set var="pageDescription"><spring:message code="meta.description.index"/></c:set>
<c:set var="selectedQuestions" value="${reportForm.questionTypes}"/>
<c:set var="storyMax" value="4000"/>
<spring:message code="validation.company.required" var="msgCompany"/>
<spring:message code="validation.role.required" var="msgRole"/>
<spring:message code="validation.round.required" var="msgRound"/>
<spring:message code="validation.outcome.required" var="msgOutcome"/>
<spring:message code="validation.story.required" var="msgStory"/>
<spring:message code="validation.story.size" var="msgStorySize"/>
<spring:message code="validation.confirm.required" var="msgConsent"/>
<spring:message code="form.submitting" var="msgSubmitting"/>
<spring:message code="form.error.summary" var="msgSummary"/>
<!DOCTYPE html>
<html lang="${pageLang}" class="no-js">
<head>
<%@ include file="fragments/head.jspf" %>
</head>
<body class="page page--index">
<%@ include file="fragments/topbar.jspf" %>
<main id="main" tabindex="-1">
  <section class="intro" aria-labelledby="hero-title">
    <h1 class="display" id="hero-title">
      <span class="display__text"><spring:message code="home.title"/></span>
    </h1>
  </section>

  <section class="debrief" aria-labelledby="debrief-heading">
    <div class="debrief__top">
      <p class="eyebrow"><span class="eyebrow__dot" aria-hidden="true"></span><spring:message code="home.form.kicker"/></p>
      <p class="meta"><spring:message code="home.form.meta"/><span class="meta__divider" aria-hidden="true">/</span><span class="meta__req"><spring:message code="form.requiredNote"/></span></p>
    </div>
    <h2 id="debrief-heading" class="sr-only"><spring:message code="home.form.heading"/></h2>

    <c:set var="hasBindErrors" value="false"/>
    <spring:hasBindErrors name="reportForm">
      <c:set var="hasBindErrors" value="true"/>
    </spring:hasBindErrors>
    <c:if test="${submitBlocked or hasBindErrors}">
      <div class="form-alert" id="form-alert" role="alert" tabindex="-1">
        <span class="form-alert__mark" aria-hidden="true">!</span>
        <div>
          <c:if test="${submitBlocked}"><p><spring:message code="form.error.rateLimited"/></p></c:if>
          <c:if test="${hasBindErrors}"><p><spring:message code="form.error.summary"/></p></c:if>
        </div>
      </div>
    </c:if>

    <div class="sr-only" id="form-messages" aria-hidden="true"
      data-msg-company="${fn:escapeXml(msgCompany)}"
      data-msg-role="${fn:escapeXml(msgRole)}"
      data-msg-round="${fn:escapeXml(msgRound)}"
      data-msg-outcome="${fn:escapeXml(msgOutcome)}"
      data-msg-story="${fn:escapeXml(msgStory)}"
      data-msg-story-size="${fn:escapeXml(msgStorySize)}"
      data-msg-consent="${fn:escapeXml(msgConsent)}"
      data-msg-submitting="${fn:escapeXml(msgSubmitting)}"
      data-msg-summary="${fn:escapeXml(msgSummary)}"></div>
    <p class="sr-only" id="form-status" role="status" aria-live="polite"></p>

    <form:form modelAttribute="reportForm" id="debrief-form" cssClass="debrief__form" method="post" action="${ctx}/reports">

      <div class="hp" aria-hidden="true">
        <label for="website">Website</label>
        <input type="text" id="website" name="website" tabindex="-1" autocomplete="off">
      </div>

      <section class="step step--room" aria-labelledby="step-room">
        <div class="step__head">
          <h2 class="step__title" id="step-room"><spring:message code="form.step.room"/></h2>
        </div>
        <div class="step__body">
          <p class="fill-line">
            <span class="word"><spring:message code="form.sentence.at"/></span>
            <span class="fill fill--company">
              <label class="sr-only" for="company"><spring:message code="form.company.label"/></label>
              <input class="fill__input" id="company" name="company" type="text" value="${fn:escapeXml(reportForm.company)}" placeholder="<spring:message code='form.company.placeholder'/>" maxlength="80" autocomplete="organization" required aria-describedby="company-error" data-field="company">
              <span class="fill__line" aria-hidden="true"></span>
            </span>
            <span class="word"><spring:message code="form.sentence.role"/></span>
            <span class="fill fill--role">
              <label class="sr-only" for="role"><spring:message code="form.role.label"/></label>
              <input class="fill__input" id="role" name="role" type="text" value="${fn:escapeXml(reportForm.role)}" placeholder="<spring:message code='form.role.placeholder'/>" maxlength="100" autocomplete="organization-title" required aria-describedby="role-error" data-field="role">
              <span class="fill__line" aria-hidden="true"></span>
            </span>
            <span class="word fill-line__period" aria-hidden="true"><spring:message code="form.sentence.period"/></span>
          </p>
          <div class="field-errors field-errors--inline">
            <div class="field-error" id="company-error"><form:errors path="company"/></div>
            <div class="field-error" id="role-error"><form:errors path="role"/></div>
          </div>

          <fieldset class="choice-group" id="round-group">
            <legend class="micro-label"><spring:message code="form.round.legend"/> <span class="req" aria-hidden="true">*</span></legend>
            <div class="chips">
              <label class="chip"><input class="chip__input" type="radio" name="round" value="phone" ${reportForm.round eq 'phone' ? 'checked' : ''} required data-field="round"><span class="chip__face"><spring:message code="round.phone"/></span></label>
              <label class="chip"><input class="chip__input" type="radio" name="round" value="technical" ${reportForm.round eq 'technical' ? 'checked' : ''} required data-field="round"><span class="chip__face"><spring:message code="round.technical"/></span></label>
              <label class="chip"><input class="chip__input" type="radio" name="round" value="takehome" ${reportForm.round eq 'takehome' ? 'checked' : ''} required data-field="round"><span class="chip__face"><spring:message code="round.takehome"/></span></label>
              <label class="chip"><input class="chip__input" type="radio" name="round" value="systemdesign" ${reportForm.round eq 'systemdesign' ? 'checked' : ''} required data-field="round"><span class="chip__face"><spring:message code="round.systemdesign"/></span></label>
              <label class="chip"><input class="chip__input" type="radio" name="round" value="behavioral" ${reportForm.round eq 'behavioral' ? 'checked' : ''} required data-field="round"><span class="chip__face"><spring:message code="round.behavioral"/></span></label>
              <label class="chip"><input class="chip__input" type="radio" name="round" value="final" ${reportForm.round eq 'final' ? 'checked' : ''} required data-field="round"><span class="chip__face"><spring:message code="round.final"/></span></label>
              <label class="chip"><input class="chip__input" type="radio" name="round" value="other" ${reportForm.round eq 'other' ? 'checked' : ''} required data-field="round"><span class="chip__face"><spring:message code="round.other"/></span></label>
            </div>
            <div class="field-error" id="round-error"><form:errors path="round"/></div>
          </fieldset>

          <div class="where-row">
            <fieldset class="choice-group choice-group--compact">
              <legend class="micro-label"><spring:message code="form.workmode.legend"/></legend>
              <div class="chips chips--small">
                <label class="chip chip--small"><input class="chip__input" type="radio" name="workMode" value="onsite" ${reportForm.workMode eq 'onsite' ? 'checked' : ''}><span class="chip__face"><spring:message code="mode.onsite"/></span></label>
                <label class="chip chip--small"><input class="chip__input" type="radio" name="workMode" value="hybrid" ${reportForm.workMode eq 'hybrid' ? 'checked' : ''}><span class="chip__face"><spring:message code="mode.hybrid"/></span></label>
                <label class="chip chip--small"><input class="chip__input" type="radio" name="workMode" value="remote" ${reportForm.workMode eq 'remote' ? 'checked' : ''}><span class="chip__face"><spring:message code="mode.remote"/></span></label>
              </div>
              <div class="field-error" id="workMode-error"><form:errors path="workMode"/></div>
            </fieldset>
            <p class="fill fill--location">
              <label class="micro-label" for="location"><spring:message code="form.location.label"/> <span class="micro-label__optional"><spring:message code="form.optional.paren"/></span></label>
              <input class="fill__input" id="location" name="location" type="text" value="${fn:escapeXml(reportForm.location)}" placeholder="<spring:message code='form.location.placeholder'/>" maxlength="80" autocomplete="address-level2" aria-describedby="location-error" data-field="location">
              <span class="fill__line" aria-hidden="true"></span>
              <span class="field-error" id="location-error"><form:errors path="location"/></span>
            </p>
          </div>
        </div>
      </section>

      <section class="step step--answer" aria-labelledby="step-answer">
        <div class="step__head">
          <h2 class="step__title" id="step-answer"><spring:message code="form.step.answer"/></h2>
        </div>
        <div class="step__body">
          <div class="answer-grid">
            <fieldset class="choice-group" id="outcome-group">
              <legend class="micro-label"><spring:message code="form.outcome.legend"/> <span class="req" aria-hidden="true">*</span></legend>
              <div class="chips">
                <label class="chip"><input class="chip__input" type="radio" name="outcome" value="offer" ${reportForm.outcome eq 'offer' ? 'checked' : ''} required data-field="outcome"><span class="chip__face"><spring:message code="outcome.offer"/></span></label>
                <label class="chip"><input class="chip__input" type="radio" name="outcome" value="rejection" ${reportForm.outcome eq 'rejection' ? 'checked' : ''} required data-field="outcome"><span class="chip__face"><spring:message code="outcome.rejection"/></span></label>
                <label class="chip"><input class="chip__input" type="radio" name="outcome" value="ghosted" ${reportForm.outcome eq 'ghosted' ? 'checked' : ''} required data-field="outcome"><span class="chip__face"><spring:message code="outcome.ghosted"/></span></label>
                <label class="chip"><input class="chip__input" type="radio" name="outcome" value="waiting" ${reportForm.outcome eq 'waiting' ? 'checked' : ''} required data-field="outcome"><span class="chip__face"><spring:message code="outcome.waiting"/></span></label>
                <label class="chip"><input class="chip__input" type="radio" name="outcome" value="withdrew" ${reportForm.outcome eq 'withdrew' ? 'checked' : ''} required data-field="outcome"><span class="chip__face"><spring:message code="outcome.withdrew"/></span></label>
              </div>
              <div class="field-error" id="outcome-error"><form:errors path="outcome"/></div>
            </fieldset>

            <p class="select-field">
              <label class="micro-label" for="heardBack"><spring:message code="form.heardback.label"/> <span class="micro-label__optional"><spring:message code="form.optional.paren"/></span></label>
              <span class="select-wrap">
                <select id="heardBack" name="heardBack" data-field="heardBack">
                  <option value=""><spring:message code="form.heardback.none"/></option>
                  <option value="days" ${reportForm.heardBack eq 'days' ? 'selected' : ''}><spring:message code="heardback.days"/></option>
                  <option value="week" ${reportForm.heardBack eq 'week' ? 'selected' : ''}><spring:message code="heardback.week"/></option>
                  <option value="weeks" ${reportForm.heardBack eq 'weeks' ? 'selected' : ''}><spring:message code="heardback.weeks"/></option>
                  <option value="month" ${reportForm.heardBack eq 'month' ? 'selected' : ''}><spring:message code="heardback.month"/></option>
                  <option value="never" ${reportForm.heardBack eq 'never' ? 'selected' : ''}><spring:message code="heardback.never"/></option>
                  <option value="stillwaiting" ${reportForm.heardBack eq 'stillwaiting' ? 'selected' : ''}><spring:message code="heardback.stillwaiting"/></option>
                </select>
                <svg class="select-wrap__arrow" width="11" height="7" viewBox="0 0 11 7" fill="none" aria-hidden="true"><path d="m1 1 4.5 4.5L10 1" stroke="currentColor" stroke-width="1.4"/></svg>
              </span>
              <span class="field-error" id="heardBack-error"><form:errors path="heardBack"/></span>
            </p>
          </div>
        </div>
      </section>

      <section class="step step--note" aria-labelledby="step-note">
        <div class="step__head">
          <h2 class="step__title" id="step-note"><spring:message code="form.step.note"/></h2>
        </div>
        <div class="step__body">
          <div class="story">
            <label class="story__label" for="story"><spring:message code="form.story.label"/> <span class="req" aria-hidden="true">*</span></label>
            <p class="story__hint" id="story-hint"><spring:message code="form.story.hint"/></p>
            <textarea class="story__input" id="story" name="story" rows="7" maxlength="${storyMax}" required placeholder="<spring:message code='form.story.placeholder'/>" aria-describedby="story-hint story-error story-count" data-field="story" data-maxlength="${storyMax}"><c:out value="${reportForm.story}"/></textarea>
            <div class="story__meta">
              <span class="field-error" id="story-error"><form:errors path="story"/></span>
              <span class="story__count" id="story-count" hidden><span class="sr-only"><spring:message code="form.story.counterLabel"/></span>&nbsp;<span data-count>0</span> / <fmt:formatNumber value="${storyMax}" pattern="#,##0"/></span>
            </div>
          </div>

          <details class="extras">
            <summary class="extras__summary">
              <span class="extras__plus" aria-hidden="true"></span>
              <span><spring:message code="form.extras.summary"/></span>
              <span class="extras__optional"><spring:message code="form.extras.optional"/></span>
            </summary>
            <div class="extras__body">
              <fieldset class="choice-group">
                <legend class="micro-label"><spring:message code="form.questions.legend"/></legend>
                <div class="chips chips--small">
                  <label class="chip chip--small"><input class="chip__input" type="checkbox" name="questionTypes" value="algorithm" ${not empty selectedQuestions and selectedQuestions.contains('algorithm') ? 'checked' : ''}><span class="chip__face"><spring:message code="question.algorithm"/></span></label>
                  <label class="chip chip--small"><input class="chip__input" type="checkbox" name="questionTypes" value="systemdesign" ${not empty selectedQuestions and selectedQuestions.contains('systemdesign') ? 'checked' : ''}><span class="chip__face"><spring:message code="question.systemdesign"/></span></label>
                  <label class="chip chip--small"><input class="chip__input" type="checkbox" name="questionTypes" value="takehome" ${not empty selectedQuestions and selectedQuestions.contains('takehome') ? 'checked' : ''}><span class="chip__face"><spring:message code="question.takehome"/></span></label>
                  <label class="chip chip--small"><input class="chip__input" type="checkbox" name="questionTypes" value="behavioral" ${not empty selectedQuestions and selectedQuestions.contains('behavioral') ? 'checked' : ''}><span class="chip__face"><spring:message code="question.behavioral"/></span></label>
                  <label class="chip chip--small"><input class="chip__input" type="checkbox" name="questionTypes" value="domain" ${not empty selectedQuestions and selectedQuestions.contains('domain') ? 'checked' : ''}><span class="chip__face"><spring:message code="question.domain"/></span></label>
                  <label class="chip chip--small"><input class="chip__input" type="checkbox" name="questionTypes" value="culture" ${not empty selectedQuestions and selectedQuestions.contains('culture') ? 'checked' : ''}><span class="chip__face"><spring:message code="question.culture"/></span></label>
                  <label class="chip chip--small"><input class="chip__input" type="checkbox" name="questionTypes" value="casestudy" ${not empty selectedQuestions and selectedQuestions.contains('casestudy') ? 'checked' : ''}><span class="chip__face"><spring:message code="question.casestudy"/></span></label>
                </div>
                <div class="field-error" id="questionTypes-error"><form:errors path="questionTypes"/></div>
              </fieldset>

              <fieldset class="choice-group">
                <legend class="micro-label"><spring:message code="form.vibe.legend"/> <span class="micro-label__optional"><spring:message code="form.optional.paren"/></span></legend>
                <div class="chips chips--small">
                  <label class="chip chip--small"><input class="chip__input" type="radio" name="vibe" value="warm" ${reportForm.vibe eq 'warm' ? 'checked' : ''}><span class="chip__face"><spring:message code="vibe.warm"/></span></label>
                  <label class="chip chip--small"><input class="chip__input" type="radio" name="vibe" value="neutral" ${reportForm.vibe eq 'neutral' ? 'checked' : ''}><span class="chip__face"><spring:message code="vibe.neutral"/></span></label>
                  <label class="chip chip--small"><input class="chip__input" type="radio" name="vibe" value="cold" ${reportForm.vibe eq 'cold' ? 'checked' : ''}><span class="chip__face"><spring:message code="vibe.cold"/></span></label>
                  <label class="chip chip--small"><input class="chip__input" type="radio" name="vibe" value="rushed" ${reportForm.vibe eq 'rushed' ? 'checked' : ''}><span class="chip__face"><spring:message code="vibe.rushed"/></span></label>
                  <label class="chip chip--small"><input class="chip__input" type="radio" name="vibe" value="unclear" ${reportForm.vibe eq 'unclear' ? 'checked' : ''}><span class="chip__face"><spring:message code="vibe.unclear"/></span></label>
                </div>
                <div class="field-error" id="vibe-error"><form:errors path="vibe"/></div>
              </fieldset>

              <div class="extras__row">
                <p class="fill fill--salary">
                  <label class="micro-label" for="salary"><spring:message code="form.salary.label"/></label>
                  <input class="fill__input" id="salary" name="salary" type="text" value="${fn:escapeXml(reportForm.salary)}" placeholder="<spring:message code='form.salary.placeholder'/>" maxlength="80" aria-describedby="salary-error" data-field="salary">
                  <span class="fill__line" aria-hidden="true"></span>
                  <span class="field-error" id="salary-error"><form:errors path="salary"/></span>
                </p>
              </div>
            </div>
          </details>

          <div class="consent">
            <label class="check">
              <input class="check__input" id="confirm" name="confirm" type="checkbox" ${reportForm.confirm ? 'checked' : ''} required aria-describedby="confirm-error" data-field="confirm">
              <span class="check__box" aria-hidden="true"></span>
              <span class="check__text"><spring:message code="form.confirm"/></span>
            </label>
            <div class="field-error" id="confirm-error"><form:errors path="confirm"/></div>
          </div>

          <div class="debrief__submit">
            <button class="btn btn--primary" type="submit" data-submit>
              <span class="btn__label"><spring:message code="form.submit"/></span>
              <svg class="btn__arrow" width="18" height="14" viewBox="0 0 18 14" fill="none" aria-hidden="true"><path d="M1 7h15M10 1l6 6-6 6" stroke="currentColor" stroke-width="1.5"/></svg>
            </button>
            <span class="stamp" aria-hidden="true"><spring:message code="form.anonymousStamp"/></span>
          </div>
        </div>
      </section>
    </form:form>

    <p class="trust">
      <svg class="trust__icon" width="15" height="17" viewBox="0 0 15 17" fill="none" aria-hidden="true"><rect x="1" y="7" width="13" height="9" stroke="currentColor"/><path d="M4 7V4.5a3.5 3.5 0 0 1 7 0V7" stroke="currentColor"/></svg>
      <span><strong><spring:message code="form.trust.strong"/></strong> <spring:message code="form.trust.rest"/></span>
    </p>
  </section>

  <%-- Optional component slot: extensions render only when a component bean is active. --%>
  <c:forEach items="${pageExtensions}" var="extension">
    <c:if test="${extension.page eq 'index'}"><jsp:include page="${extension.fragment}"/></c:if>
  </c:forEach>

  <section class="lookup" aria-labelledby="lookup-heading">
    <div class="lookup__search">
      <h2 class="sr-only" id="lookup-heading"><spring:message code="home.lookup.label"/></h2>
      <p class="eyebrow eyebrow--quiet"><spring:message code="home.lookup.eyebrow"/></p>
      <form class="quiet-search" action="${ctx}/browse" method="get" role="search">
        <label class="sr-only" for="quiet-q"><spring:message code="home.lookup.label"/></label>
        <span class="quiet-search__field">
          <input id="quiet-q" name="q" type="search" placeholder="<spring:message code='home.lookup.placeholder'/>" maxlength="120">
          <span class="quiet-search__line" aria-hidden="true"></span>
        </span>
        <button class="quiet-search__submit" type="submit">
          <span><spring:message code="home.lookup.action"/></span>
          <svg width="12" height="12" viewBox="0 0 13 13" fill="none" aria-hidden="true"><path d="M2 11 11 2M4 2h7v7" stroke="currentColor" stroke-width="1.4"/></svg>
        </button>
      </form>
      <a class="quiet-link" href="${ctx}/browse"><spring:message code="home.lookup.all"/></a>
    </div>

    <div class="recent">
      <p class="eyebrow eyebrow--quiet"><spring:message code="home.recent.eyebrow"/></p>
      <c:choose>
        <c:when test="${not empty recentReports}">
          <ul class="recent__list" role="list">
            <c:forEach items="${recentReports}" var="note">
              <li>
                <a class="mini-note" href="${ctx}/reports/${note.id}">
                  <span class="mini-note__main">
                    <span class="mini-note__company"><c:out value="${note.company}"/></span>
                    <span class="mini-note__role"><c:out value="${note.role}"/></span>
                  </span>
                  <span class="mini-note__meta"><span><spring:message code="round.${note.round}"/></span><span aria-hidden="true">·</span><span><c:out value="${note.createdAtLabel}"/></span></span>
                  <span class="mini-note__arrow" aria-hidden="true">↗</span>
                </a>
              </li>
            </c:forEach>
          </ul>
        </c:when>
        <c:otherwise>
          <p class="recent__empty"><spring:message code="home.recent.empty"/></p>
        </c:otherwise>
      </c:choose>
    </div>
  </section>
</main>
<%@ include file="fragments/footer.jspf" %>
</body>
</html>
