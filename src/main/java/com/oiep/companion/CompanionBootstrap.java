package com.oiep.companion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Logs the effective state of the optional AI companion component at startup.
 *
 * <p>The log line is the operator's one place to see whether the component is fully wired,
 * partially configured or deliberately off. Only configuration *state* is logged: tokens and
 * request bodies never appear. When the component is disabled the bean does not exist and
 * the application starts exactly as it did before the component was added.</p>
 */
@Component
@Conditional(CompanionConditions.Enabled.class)
public class CompanionBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CompanionBootstrap.class);

    private final CompanionProperties properties;

    public CompanionBootstrap(CompanionProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String serviceOrigin = properties.serviceOrigin();
        log.info("AI companion component enabled: baseUrlConfigured={} serviceOrigin={} "
                        + "userHelper={} adminHelper={} readApi={}",
                !serviceOrigin.isEmpty(),
                serviceOrigin.isEmpty() ? "(invalid or missing)" : serviceOrigin,
                properties.userHelperUsable(),
                properties.adminHelperUsable(),
                properties.readApiUsable());

        if (!properties.userHelperUsable()) {
            if (!properties.getUserHelper().isEnabled()) {
                log.info("AI companion user helper is switched off (OIEP_COMPANION_USER_HELPER_ENABLED=false)");
            } else if (properties.widgetOrigin().isEmpty()) {
                log.warn("AI companion user helper is not shown: OIEP_COMPANION_WIDGET_URL / "
                        + "OIEP_COMPANION_BASE_URL is missing or not a plain http(s) URL");
            }
        }

        if (properties.getAdminHelper().isEnabled() && !properties.adminHelperUsable()) {
            log.warn("AI companion admin helper is configured but disabled: tokenProblem={} "
                            + "baseUrlValid={} adminTransportAllowed={}",
                    CompanionSettingsSupport.secretProblem(properties.getAdminHelper().getToken()),
                    !properties.serviceOrigin().isEmpty(),
                    CompanionSettingsSupport.adminTransportAllowed(properties.getBaseUrl(),
                            properties.getAdminHelper().isAllowInsecureHttp()));
        } else if (properties.adminHelperUsable()
                && !CompanionSettingsSupport.adminTransportAllowed(properties.getBaseUrl(), false)) {
            log.warn("AI companion admin helper is using explicitly permitted unencrypted HTTP; "
                    + "the operator token and requests can be intercepted");
        }

        if (properties.getReadApi().isEnabled() && !properties.readApiUsable()) {
            log.warn("AI companion read API is configured but disabled: tokenProblem={}",
                    CompanionSettingsSupport.secretProblem(properties.getReadApi().getToken()));
        }

        if (properties.userHelperUsable() && !properties.getUserHelper().getWidgetUrl().isBlank()
                && !CompanionSettingsSupport.sameOrigin(properties.getUserHelper().getWidgetUrl(),
                        properties.getBaseUrl())) {
            log.info("AI companion widget URL points at a different origin than the API base URL; "
                    + "the browser talks to the widget origin directly (CSP frame-src is set for it)");
        }
    }
}
