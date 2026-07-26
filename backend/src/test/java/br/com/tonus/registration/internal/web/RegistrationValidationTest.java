package br.com.tonus.registration.internal.web;

import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O formato de telefone é a única coisa que separa um número utilizável de um
 * campo que só vai falhar lá na frente, na hora de mandar WhatsApp. Varrer os
 * casos aqui é mais barato e mais completo que um round-trip HTTP por variação.
 */
class RegistrationValidationTest {

    private static final Pattern PHONE = Pattern.compile(RegistrationValidation.PHONE_E164);

    @ParameterizedTest
    @ValueSource(strings = {
            "+5511999990000",   // celular BR com DDD
            "+551133334444",    // fixo BR
            "+12125551234",     // fora do BR
            "+123456789",       // mínimo aceito (8 dígitos)
            "+123456789012345", // máximo aceito (15 dígitos)
    })
    void acceptsE164(String phone) {
        assertThat(PHONE.matcher(phone).matches()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "11999990000",        // sem o +, não dá para saber o país
            "+55 11 99999-0000",  // formatação humana
            "+55(11)999990000",
            "+0511999990000",     // país não começa com zero
            "+1234567",           // curto demais
            "+1234567890123456",  // longo demais
            "+55abc99990000",
            "telefone",
            "",
            "+",
    })
    void rejectsAnythingElse(String phone) {
        assertThat(PHONE.matcher(phone).matches()).isFalse();
    }
}
