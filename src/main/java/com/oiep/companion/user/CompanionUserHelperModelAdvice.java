package com.oiep.companion.user;

import com.oiep.companion.CompanionConditions;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the user-helper view model to the page that includes the component fragment.
 *
 * <p>{@code index.jsp} includes fragments through a generic loop, so the including page does
 * not pass any attribute to the fragment. The fragment reads {@code companionUserHelper} from
 * the model instead; the attribute only exists while the companion condition matches, so a
 * disabled or misconfigured instance never renders the helper.</p>
 */
@ControllerAdvice
@Conditional(CompanionConditions.UserHelper.class)
public class CompanionUserHelperModelAdvice {

    private final CompanionUserHelperExtension extension;

    public CompanionUserHelperModelAdvice(CompanionUserHelperExtension extension) {
        this.extension = extension;
    }

    @ModelAttribute("companionUserHelper")
    public CompanionUserHelperModel companionUserHelper() {
        return new CompanionUserHelperModel(extension.getWidgetUrl(), extension.getWidgetOrigin());
    }
}
