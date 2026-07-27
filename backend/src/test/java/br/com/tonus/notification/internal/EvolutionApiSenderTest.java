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
 * O adapter é o único ponto do sistema que fala HTTP com fora, e o que ele
 * promete ao resto do módulo é estreito: entregou, ou levantou
 * {@link WhatsAppDeliveryException}. Traduzir a falha é o que faz o rollback
 * acontecer e a publicação ficar incompleta para o registry reprocessar — um
 * erro que escapasse em outro tipo passaria por falha de programação, não por
 * indisponibilidade.
 *
 * Testado com MockRestServiceServer, do próprio spring-boot-starter-test, em
 * vez de WireMock: não precisa de servidor de verdade nem de dependência nova.
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
     * O cadastro guarda E.164 com "+"; a Evolution documenta o campo em dígitos
     * puros. A conversão é do adapter, e tem teste próprio porque é invisível
     * em qualquer outro lugar — nenhum teste de listener olha o corpo HTTP, e o
     * erro só apareceria como mensagem que não chega ao aluno.
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
     * Credencial errada também é falha de entrega, e não erro de programação:
     * a mensagem não saiu, e reprocessar depois de corrigir a chave é
     * exatamente o comportamento desejado.
     */
    @Test
    void unauthorizedBecomesDeliveryException() {
        server.expect(requestTo(BASE_URL + "/message/sendText/" + INSTANCE))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> sender.send("+5511988887777", "Oi"))
                .isInstanceOf(WhatsAppDeliveryException.class);
    }

    /**
     * Evolution inalcançável — container fora do ar, DNS que não resolve — é
     * IOException dentro do RestClient, não resposta HTTP. Sem esta tradução a
     * exceção subiria como ResourceAccessException e o listener quebraria por
     * um tipo que ninguém espera.
     */
    @Test
    void unreachableServerBecomesDeliveryException() {
        EvolutionApiSender offline = new EvolutionApiSender(
                RestClient.builder().baseUrl("http://localhost:1").build(), API_KEY, INSTANCE);

        assertThatThrownBy(() -> offline.send("+5511988887777", "Oi"))
                .isInstanceOf(WhatsAppDeliveryException.class);
    }

    /** Nem telefone nem conteúdo da mensagem na exceção que vai para o log. */
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
