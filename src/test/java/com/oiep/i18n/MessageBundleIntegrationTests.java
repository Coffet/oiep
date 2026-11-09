package com.oiep.i18n;

import com.oiep.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;

import java.util.Locale;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression cover for the message-bundle locale selection. The host JVM on the development
 * machine defaults to zh_HK, so this catches the classic "English request renders the Chinese
 * bundle because fallbackToSystemLocale wins over the root bundle" configuration mistake.
 */
class MessageBundleIntegrationTests extends IntegrationTestSupport {

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    @Autowired
    private MessageSource messageSource;

    @Test
    void englishLocaleResolvesTheRootEnglishBundle() {
        String title = messageSource.getMessage("home.title", null, Locale.ENGLISH);
        assertThat(title).isNotBlank();
        assertThat(CJK.matcher(title).find())
                .as("English locale must not resolve a Chinese message (host default locale=%s)", Locale.getDefault())
                .isFalse();
    }

    @Test
    void chineseLocaleResolvesTheChineseBundle() {
        String title = messageSource.getMessage("home.title", null, Locale.forLanguageTag("zh-HK"));
        assertThat(title).isNotBlank();
        assertThat(CJK.matcher(title).find())
                .as("zh-HK locale must resolve a Chinese message")
                .isTrue();
    }

    @Test
    void validationMessageCodesExistInBothLocales() {
        String english = messageSource.getMessage("validation.company.required", null, Locale.ENGLISH);
        String chinese = messageSource.getMessage("validation.company.required", null, Locale.forLanguageTag("zh-HK"));
        assertThat(english).isNotBlank();
        assertThat(chinese).isNotBlank();
        assertThat(english).isNotEqualTo(chinese);
    }

    @Test
    void typeMismatchMessagesAreLocalizedForTheConfirmCheckbox() {
        String english = messageSource.getMessage("typeMismatch.confirm", null, Locale.ENGLISH);
        String chinese = messageSource.getMessage("typeMismatch.confirm", null, Locale.forLanguageTag("zh-HK"));
        assertThat(english).isNotBlank();
        assertThat(chinese).isNotBlank();
        assertThat(english).isNotEqualTo(chinese);
        assertThat(english).doesNotContain("Failed to convert");
    }
}
