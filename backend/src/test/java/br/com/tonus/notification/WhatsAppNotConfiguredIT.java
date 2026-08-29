package br.com.tonus.notification;

import br.com.tonus.registration.AbstractRegistrationIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An environment with no gateway configured — which is every developer's, since
 * application-test.yml leaves base-url and api-key empty.
 *
 * Deliberately does NOT import {@code WhatsAppAdminTestConfig}: the fake would
 * hide the very branch under test. This is the only class where the real bean
 * wiring decides the answer, and it is the first of SPEC-014's five screen
 * states — without a 503 the front has nothing to distinguish "incomplete
 * deployment" from "gateway broken".
 */
class WhatsAppNotConfiguredIT extends AbstractRegistrationIntegrationTest {

    /**
     * 503 and not 500: the deployment is incomplete, which is the only
     * condition where this API admits it cannot answer. And not 404 either —
     * that would read as a product that does not have the feature.
     */
    @Test
    void everyRouteAnswers503WhenThereIsNoGatewayConfigured() throws Exception {
        mockMvc.perform(get("/whatsapp/status").cookie(admin))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Gateway não configurado"));

        mockMvc.perform(get("/whatsapp/instances").cookie(admin))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(post("/whatsapp/session").cookie(admin).with(csrf()))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(delete("/whatsapp/session").cookie(admin).with(csrf()))
                .andExpect(status().isServiceUnavailable());
    }

    /** Not configured is still not an invitation: the role check comes first. */
    @Test
    void roleStillDecidesBeforeTheGatewayDoes() throws Exception {
        mockMvc.perform(get("/whatsapp/status")
                        .cookie(teacherCookie(unique("whatsapp-nc-teacher") + "@tonus.com")))
                .andExpect(status().isForbidden());
    }

    /**
     * The problem detail says the environment is incomplete and nothing more.
     * A message naming the property would tell an authenticated ADMIN — and
     * anyone who got hold of that session — about the deployment's shape.
     */
    @Test
    void theProblemDetailNamesNoConfigurationValue() throws Exception {
        String body = mockMvc.perform(get("/whatsapp/status").cookie(admin))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("base-url")
                .doesNotContain("api-key")
                .doesNotContain("EVOLUTION_");
    }
}
