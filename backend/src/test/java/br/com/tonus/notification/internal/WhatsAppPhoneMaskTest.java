package br.com.tonus.notification.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mask is the last thing between the studio's contact list and a screen,
 * and it is invisible from anywhere else — no endpoint test looks at the middle
 * digits. Its own test is the only place the rule is actually stated.
 */
class WhatsAppPhoneMaskTest {

    @Test
    void keepsOnlyWhatIsNeededToRecognizeTheDevice() {
        assertThat(WhatsAppAdminService.mask("+5511988887777")).isEqualTo("+55 11 9****-**77");
    }

    /** Evolution hands the number back as a JID, not as E.164. */
    @Test
    void acceptsAJidAndNotOnlyE164() {
        assertThat(WhatsAppAdminService.mask("5511988887777@s.whatsapp.net"))
                .isEqualTo("+55 11 9****-**77");
    }

    @Test
    void noPhoneStaysAbsent() {
        assertThat(WhatsAppAdminService.mask(null)).isNull();
    }

    /**
     * Anything too short collapses instead of leaking most of itself: masking
     * "the usual way" would return a five-digit value nearly intact.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "123", "+55119", "abc"})
    void tooShortToMaskMeaningfullyCollapses(String value) {
        assertThat(WhatsAppAdminService.mask(value)).isEqualTo("****");
    }

    /** The property that matters, stated directly: the middle never survives. */
    @Test
    void theMiddleDigitsNeverSurvive() {
        String masked = WhatsAppAdminService.mask("+5511988887777");

        assertThat(masked).doesNotContain("8888");
        assertThat(masked).doesNotContain("988887777");
    }
}
