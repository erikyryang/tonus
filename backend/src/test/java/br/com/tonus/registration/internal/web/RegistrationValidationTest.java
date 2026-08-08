package br.com.tonus.registration.internal.web;

import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phone format is the only thing that separates a usable number from a
 * field that will only fail further ahead, at WhatsApp sending time. Sweeping the
 * cases here is cheaper and more complete than an HTTP round-trip per variation.
 */
class RegistrationValidationTest {

    private static final Pattern PHONE = Pattern.compile(RegistrationValidation.PHONE_E164);

    @ParameterizedTest
    @ValueSource(strings = {
            "+5511999990000",   // BR mobile with area code
            "+551133334444",    // BR landline
            "+12125551234",     // outside BR
            "+123456789",       // minimum accepted (8 digits)
            "+123456789012345", // maximum accepted (15 digits)
    })
    void acceptsE164(String phone) {
        assertThat(PHONE.matcher(phone).matches()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "11999990000",        // without the +, there is no way to know the country
            "+55 11 99999-0000",  // human formatting
            "+55(11)999990000",
            "+0511999990000",     // country does not start with zero
            "+1234567",           // too short
            "+1234567890123456",  // too long
            "+55abc99990000",
            "telefone",
            "",
            "+",
    })
    void rejectsAnythingElse(String phone) {
        assertThat(PHONE.matcher(phone).matches()).isFalse();
    }
}
