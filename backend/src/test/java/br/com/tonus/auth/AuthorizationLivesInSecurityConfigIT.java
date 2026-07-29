package br.com.tonus.auth;

import br.com.tonus.AbstractIntegrationTest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quem pode chamar o quê é decidido em um lugar só: o {@code SecurityConfig}.
 *
 * A regra existe porque as duas formas funcionam, e é essa a armadilha. Com
 * autorização espalhada em anotação de controller, responder "quem enxerga a
 * agenda do estúdio?" exige abrir todos os controllers e confiar que nenhum foi
 * esquecido — e o esquecido não falha, ele libera. Concentrada no filtro, a
 * configuração é o inventário completo da superfície da API, e o que não está
 * lá cai na regra final.
 *
 * O teste é de reflexão, e não de comportamento, de propósito: um endpoint novo
 * com {@code @PreAuthorize} passaria em qualquer teste de acesso que alguém
 * escrevesse para ele. O que precisa quebrar é a reintrodução do padrão.
 *
 * {@code @EnableMethodSecurity} continua ligado de propósito: desligá-lo faria
 * um {@code @PreAuthorize} futuro virar um no-op silencioso — o pior desfecho
 * possível para uma anotação de autorização. Aqui ele falha alto.
 */
class AuthorizationLivesInSecurityConfigIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void noControllerDeclaresItsOwnAuthorization() {
        List<String> offenders = new ArrayList<>();

        for (String name : context.getBeanNamesForAnnotation(RestController.class)) {
            Class<?> controller = context.getType(name);
            if (controller == null) {
                continue;
            }
            if (controller.isAnnotationPresent(PreAuthorize.class)) {
                offenders.add(controller.getSimpleName());
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (method.isAnnotationPresent(PreAuthorize.class)) {
                    offenders.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertThat(offenders)
                .as("autorização declarada no controller; mova a regra para o SecurityConfig")
                .isEmpty();
    }
}
