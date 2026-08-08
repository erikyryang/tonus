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
 * Who can call what is decided in one single place: {@code SecurityConfig}.
 *
 * The rule exists because both approaches work, and that's exactly the trap.
 * With authorization scattered across controller annotations, answering
 * "who can see the studio's schedule?" requires opening every controller and
 * trusting that none was forgotten — and the forgotten one doesn't fail, it
 * lets through. Concentrated in the filter, the configuration is the
 * complete inventory of the API's surface, and whatever isn't there falls
 * into the final rule.
 *
 * The test is a reflection test, not a behavior test, on purpose: a new
 * endpoint with {@code @PreAuthorize} would pass any access test someone
 * wrote for it. What needs to break is the reintroduction of the pattern.
 *
 * {@code @EnableMethodSecurity} stays enabled on purpose: turning it off
 * would make a future {@code @PreAuthorize} become a silent no-op — the
 * worst possible outcome for an authorization annotation. Here it fails
 * loudly.
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
