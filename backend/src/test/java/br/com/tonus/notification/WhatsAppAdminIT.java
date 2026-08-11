package br.com.tonus.notification;

import br.com.tonus.notification.support.RecordingWhatsAppSessionGateway;
import br.com.tonus.notification.support.WhatsAppAdminTestConfig;
import br.com.tonus.registration.AbstractRegistrationIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administration of the WhatsApp gateway by the ADMIN (SPEC-013).
 *
 * The gateway is faked ({@link RecordingWhatsAppSessionGateway}) because what
 * is under test is the endpoint and the state machine around it, not HTTP
 * against Evolution — that has its own contract test, against the real image.
 */
@Import(WhatsAppAdminTestConfig.class)
class WhatsAppAdminIT extends AbstractRegistrationIntegrationTest {

    private static final String INSTANCE = "tonus";
    private static final String OTHER_INSTANCE = "estudio-2";
    private static final String PHONE = "+5511988887777";
    private static final String VERSION_IN_EFFECT = "2.3000.1043857760";

    @Autowired
    private RecordingWhatsAppSessionGateway gateway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetGatewayAndSettings() {
        gateway.reset();
        jdbcTemplate.update("""
                UPDATE whatsapp_settings
                SET instance_name = ?, desired_phone_version = ?, updated_at = now()
                WHERE id = 1
                """, INSTANCE, VERSION_IN_EFFECT);
    }

    /**
     * The state the environment is actually in right now: configuration points
     * at an instance the gateway never heard of, because pairing was done by
     * hand under another name.
     *
     * It has to answer 200 with a state of its own. As a 500 or an empty
     * screen, the one person who could fix it is the one who cannot see it.
     */
    @Test
    void instanceMissingFromTheGatewayIsAStateAndNotAnError() throws Exception {
        mockMvc.perform(get("/whatsapp/status").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instance").value(INSTANCE))
                .andExpect(jsonPath("$.state").value("INSTANCE_MISSING"))
                .andExpect(jsonPath("$.maskedPhone").value(nullValue()));
    }

    @Test
    void reportsTheConnectedSession() throws Exception {
        gateway.hasConnectedInstance(INSTANCE, PHONE);

        mockMvc.perform(get("/whatsapp/status").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CONNECTED"))
                .andExpect(jsonPath("$.maskedPhone").isNotEmpty());
    }

    /** The session belongs to the studio, not to a role that has students. */
    @Test
    void teacherAndStudentCannotReachTheGateway() throws Exception {
        Cookie teacher = teacherCookie(unique("whatsapp-teacher") + "@tonus.com");
        Cookie student = studentCookie(unique("whatsapp-student") + "@tonus.com");

        mockMvc.perform(get("/whatsapp/status").cookie(teacher)).andExpect(status().isForbidden());
        mockMvc.perform(get("/whatsapp/status").cookie(student)).andExpect(status().isForbidden());
        mockMvc.perform(post("/whatsapp/session").cookie(teacher)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/whatsapp/session").cookie(student)).andExpect(status().isForbidden());
    }

    @Test
    void pairingCreatesTheInstanceAndReturnsAQrCode() throws Exception {
        mockMvc.perform(post("/whatsapp/session").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CONNECTING"))
                .andExpect(jsonPath("$.qrCode").isNotEmpty());
    }

    /**
     * Pairing again by mistake cannot cost the session that is working. Whoever
     * wants to change the number disconnects first, explicitly.
     */
    @Test
    void pairingAnAlreadyConnectedSessionReturnsNoQrCodeAndKeepsIt() throws Exception {
        gateway.hasConnectedInstance(INSTANCE, PHONE);

        mockMvc.perform(post("/whatsapp/session").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CONNECTED"))
                .andExpect(jsonPath("$.qrCode").value(nullValue()));

        assertThat(gateway.stateOf(INSTANCE)).contains(
                br.com.tonus.notification.internal.SessionState.CONNECTED);
    }

    /** Logging out is not deleting: the instance survives so it can be paired again. */
    @Test
    void disconnectingKeepsTheInstance() throws Exception {
        gateway.hasConnectedInstance(INSTANCE, PHONE);

        mockMvc.perform(delete("/whatsapp/session").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DISCONNECTED"));

        assertThat(gateway.loggedOut(INSTANCE)).isTrue();
        assertThat(gateway.stateOf(INSTANCE)).isPresent();
    }

    @Test
    void listsTheInstancesThatExistInTheGateway() throws Exception {
        gateway.hasConnectedInstance(OTHER_INSTANCE, PHONE);
        gateway.hasDisconnectedInstance(INSTANCE);

        mockMvc.perform(get("/whatsapp/instances").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instances.length()").value(2))
                .andExpect(jsonPath("$.instances[?(@.name=='%s')].state".formatted(OTHER_INSTANCE))
                        .value("CONNECTED"));
    }

    /**
     * Choosing the instance is the whole point of the setting: saved, the
     * status has to start answering about the other one.
     */
    @Test
    void savingSettingsSwitchesTheInstanceInUse() throws Exception {
        gateway.hasConnectedInstance(OTHER_INSTANCE, PHONE);

        putSettings(OTHER_INSTANCE, VERSION_IN_EFFECT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instance").value(OTHER_INSTANCE))
                .andExpect(jsonPath("$.state").value("CONNECTED"));

        mockMvc.perform(get("/whatsapp/status").cookie(admin))
                .andExpect(jsonPath("$.instance").value(OTHER_INSTANCE));
    }

    /**
     * The version cannot be applied from here — it is a container-level
     * variable. Saying so is the whole value: without the flag, the ADMIN saves,
     * sees the new number on screen and concludes it took effect.
     */
    @Test
    void desiredVersionDifferentFromTheOneInEffectIsPending() throws Exception {
        putSettings(INSTANCE, "2.3000.9999999999")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneVersion.inEffect").value(VERSION_IN_EFFECT))
                .andExpect(jsonPath("$.phoneVersion.desired").value("2.3000.9999999999"))
                .andExpect(jsonPath("$.phoneVersion.pending").value(true));

        putSettings(INSTANCE, VERSION_IN_EFFECT)
                .andExpect(jsonPath("$.phoneVersion.pending").value(false));
    }

    /** An invalid version would only show itself at the next pairing, long after being saved. */
    @Test
    void invalidVersionIsRejectedAndNothingIsSaved() throws Exception {
        putSettings(INSTANCE, "tres").andExpect(status().isBadRequest());
        putSettings(INSTANCE, "").andExpect(status().isBadRequest());

        assertThat(storedVersion()).isEqualTo(VERSION_IN_EFFECT);
    }

    /**
     * A gateway that is down is an upstream failure, and the distinction from
     * 500 is what separates "Evolution fell over" from "Tônus has a bug".
     */
    @Test
    void gatewayFailureBecomes502AndLeavesSettingsIntact() throws Exception {
        gateway.goDown();

        mockMvc.perform(get("/whatsapp/status").cookie(admin))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        assertThat(storedInstance()).isEqualTo(INSTANCE);
    }

    /** Same rule as the send path: the screen only needs enough to recognize the phone. */
    @Test
    void theFullPhoneNeverLeavesTheApi() throws Exception {
        gateway.hasConnectedInstance(INSTANCE, PHONE);

        String body = mockMvc.perform(get("/whatsapp/status").cookie(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(PHONE);
        assertThat(body).doesNotContain("988887777");
        assertThat(body).contains("77");
    }

    /** Changing where the studio speaks to every student from is worth a record. */
    @Test
    void changingSettingsLeavesAnAuditTrail() throws Exception {
        long before = auditRecords();

        putSettings(OTHER_INSTANCE, VERSION_IN_EFFECT).andExpect(status().isOk());

        assertThat(auditRecords()).isGreaterThan(before);
    }

    /**
     * The single row is a constraint, not a convention — same reasoning as
     * billing_settings. With two rows, "which instance counts?" would have
     * "whichever LIMIT 1 picked" as the answer.
     */
    @Test
    void databaseRefusesASecondSettingsRow() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO whatsapp_settings (id, instance_name, desired_phone_version, created_at, updated_at)
                VALUES (2, 'outro', '2.3000.1', now(), now())
                """))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM whatsapp_settings", Integer.class)).isEqualTo(1);
    }

    /** The DB column is VARCHAR(100); without the check this would be a 500 for a typo. */
    @Test
    void overlongValuesAreRejectedAsBadRequestAndNotAsAServerError() throws Exception {
        putSettings("i".repeat(101), VERSION_IN_EFFECT).andExpect(status().isBadRequest());
        putSettings(INSTANCE, "2." + "9".repeat(40)).andExpect(status().isBadRequest());

        assertThat(storedInstance()).isEqualTo(INSTANCE);
        assertThat(storedVersion()).isEqualTo(VERSION_IN_EFFECT);
    }

    /**
     * The api-key is an environment secret. The screen shows whether the
     * gateway is configured, never with what — and an ADMIN session is not a
     * reason to hand out the credential that sends messages as the studio.
     */
    @Test
    void noEndpointEverEchoesTheApiKey() throws Exception {
        gateway.hasConnectedInstance(INSTANCE, PHONE);

        for (String route : new String[] {"/whatsapp/status", "/whatsapp/instances"}) {
            String body = mockMvc.perform(get(route).cookie(admin))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("apikey").doesNotContain("apiKey");
        }
    }

    /**
     * A gateway failure during the update must not roll the setting back: the
     * two are separate steps precisely so a broken Evolution does not undo what
     * the ADMIN just decided.
     */
    @Test
    void aGatewayThatFallsOverDuringTheUpdateDoesNotUndoTheSaving() throws Exception {
        gateway.goDown();

        putSettings(OTHER_INSTANCE, VERSION_IN_EFFECT).andExpect(status().isBadGateway());

        assertThat(storedInstance()).isEqualTo(OTHER_INSTANCE);
    }

    private long auditRecords() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_name = 'WhatsAppSettings' AND entity_id = '1'",
                Long.class);
    }

    private ResultActions putSettings(String instance, String version) throws Exception {
        return mockMvc.perform(put("/whatsapp/settings")
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"instance":"%s","desiredPhoneVersion":"%s"}
                        """.formatted(instance, version)));
    }

    private String storedVersion() {
        return jdbcTemplate.queryForObject(
                "SELECT desired_phone_version FROM whatsapp_settings WHERE id = 1", String.class);
    }

    private String storedInstance() {
        return jdbcTemplate.queryForObject(
                "SELECT instance_name FROM whatsapp_settings WHERE id = 1", String.class);
    }
}
