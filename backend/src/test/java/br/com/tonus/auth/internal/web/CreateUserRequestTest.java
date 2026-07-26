package br.com.tonus.auth.internal.web;

import br.com.tonus.auth.internal.domain.Role;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A coerência entre role e vínculo é função pura: dá para varrer as treze
 * combinações em milissegundos, sem subir Spring nem Postgres. Os testes de
 * integração cobrem que a violação vira 400 com Problem Details; a tabela
 * completa de casos fica aqui.
 */
class CreateUserRequestTest {

    @ParameterizedTest(name = "{0} com studentId={1} teacherId={2} -> valido={3}")
    @CsvSource(nullValues = "null", value = {
            // STUDENT precisa de studentId, e só dele
            "STUDENT, 1,    null, true",
            "STUDENT, null, null, false",
            "STUDENT, null, 1,    false",
            "STUDENT, 1,    1,    false",

            // TEACHER precisa de teacherId, e só dele
            "TEACHER, null, 1,    true",
            "TEACHER, null, null, false",
            "TEACHER, 1,    null, false",
            "TEACHER, 1,    1,    false",

            // ADMIN não representa aluno nem professor: nenhum vínculo
            "ADMIN,   null, null, true",
            "ADMIN,   1,    null, false",
            "ADMIN,   null, 1,    false",
            "ADMIN,   1,    1,    false",
    })
    void validatesLinkAgainstRole(Role role, Long studentId, Long teacherId, boolean expected) {
        var request = new CreateUserRequest("login@tonus.com", "s3nha", role, studentId, teacherId);

        assertThat(request.isLinkConsistentWithRole()).isEqualTo(expected);
    }

    /**
     * Role nulo é problema do @NotNull, não desta regra. Se aqui devolvesse
     * false, a resposta traria duas violações para o mesmo erro — e a segunda
     * ("role e vínculo incompatíveis") só confundiria quem esqueceu o campo.
     */
    @ParameterizedTest(name = "role nulo com studentId={0} teacherId={1}")
    @CsvSource(nullValues = "null", value = {"null, null", "1, null", "null, 1", "1, 1"})
    void staysSilentWhenRoleIsMissing(Long studentId, Long teacherId) {
        var request = new CreateUserRequest("login@tonus.com", "s3nha", null, studentId, teacherId);

        assertThat(request.isLinkConsistentWithRole()).isTrue();
    }
}
