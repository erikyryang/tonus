package br.com.tonus.notification.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

/**
 * The adapter is the only point in the system that speaks HTTP with the
 * outside, and what it promises the rest of the module is narrow: delivered, or
 * threw {@link WhatsAppDeliveryException}. Translating the failure is what makes
 * the rollback happen and the publication stay incomplete for the registry to
 * reprocess — an error that escaped in another type would pass as a programming
 * failure, not as unavailability.
 *
 * Tested with MockRestServiceServer, from spring-boot-starter-test itself,
 * instead of WireMock: it needs no real server and no new dependency.
 */
class EvolutionApiSenderTest {

    private static final String BASE_URL = "http://evolution.internal:8080";
    private static final String API_KEY = "chave-de-teste";
    private static final String INSTANCE = "tonus";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private EvolutionApiSender sender;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new EvolutionApiSender(builder.build(), API_KEY, INSTANCE);
    }

    @Test
    void postsTextToTheConfiguredInstance() {
        server.expect(requestTo(BASE_URL + "/message/sendText/" + INSTANCE))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", API_KEY))
                .andExpect(jsonPath("$.number").value("5511988887777"))
                .andExpect(jsonPath("$.text").value("Sua aula foi cancelada."))
                .andRespond(withSuccess());

        sender.send("+5511988887777", "Sua aula foi cancelada.");

        server.verify();
    }

    /**
     * The registration stores E.164 with "+"; Evolution documents the field in
     * plain digits. The conversion is the adapter's, and has its own test
     * because it is invisible anywhere else — no listener test looks at the
     * HTTP body, and the error would only show up as a message that never
     * reaches the student.
     */
    @Test
    void e164PhoneIsSentAsDigitsOnly() {
        server.expect(requestTo(BASE_URL + "/message/sendText/" + INSTANCE))
                .andExpect(jsonPath("$.number").value("5511988887777"))
                .andRespond(withSuccess());

        sender.send("+55 (11) 98888-7777", "Oi");

        server.verify();
    }

    @Test
    void serverErrorBecomesDeliveryException() {
        server.expect(requestTo(BASE_URL + "/message/sendText/" + INSTANCE))
                .andRespond(withServerError());

        assertThatThrownBy(() -> sender.send("+5511988887777", "Oi"))
                .isInstanceOf(WhatsAppDeliveryException.class);
    }

    /**
     * Wrong credentials are also a delivery failure, not a programming error:
     * the message did not go out, and reprocessing after fixing the key is
     * exactly the desired behavior.
     */
    @Test
    void unauthorizedBecomesDeliveryException() {
        server.expect(requestTo(BASE_URL + "/message/sendText/" + INSTANCE))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> sender.send("+5511988887777", "Oi"))
                .isInstanceOf(WhatsAppDeliveryException.class);
    }

    /**
     * Evolution unreachable — container down, DNS that does not resolve — is an
     * IOException inside RestClient, not an HTTP response. Without this
     * translation the exception would rise as ResourceAccessException and the
     * listener would break with a type nobody expects.
     */
    @Test
    void unreachableServerBecomesDeliveryException() {
        EvolutionApiSender offline = new EvolutionApiSender(
                RestClient.builder().baseUrl("http://localhost:1").build(), API_KEY, INSTANCE);

        assertThatThrownBy(() -> offline.send("+5511988887777", "Oi"))
                .isInstanceOf(WhatsAppDeliveryException.class);
    }

    /** Neither phone number nor message content in the exception that goes to the log. */
    @Test
    void deliveryExceptionCarriesNoPersonalData() {
        server.expect(requestTo(BASE_URL + "/message/sendText/" + INSTANCE))
                .andRespond(withServerError());

        assertThatThrownBy(() -> sender.send("+5511988887777", "Sua aula foi cancelada."))
                .isInstanceOf(WhatsAppDeliveryException.class)
                .hasMessageNotContaining("+5511988887777")
                .hasMessageNotContaining("Sua aula foi cancelada.");
    }

    @Test
    void sendsNumberAndTextOnly() {
        server.expect(requestTo(BASE_URL + "/message/sendText/" + INSTANCE))
                .andExpect(content().json("""
                        {"number":"5511988887777","text":"Oi"}
                        """, true))
                .andRespond(withSuccess());

        sender.send("+5511988887777", "Oi");

        server.verify();
    }
}
