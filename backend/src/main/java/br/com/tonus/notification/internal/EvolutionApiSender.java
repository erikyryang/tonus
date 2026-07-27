package br.com.tonus.notification.internal;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adapter da Evolution API.
 *
 * Traduz qualquer falha de HTTP para WhatsAppDeliveryException. A tradução é o
 * ponto: quem chama precisa distinguir "não entregou" de erro de programação,
 * porque só o primeiro deve derrubar a transação e voltar pelo reprocessamento.
 */
class EvolutionApiSender implements WhatsAppSender {

    private final RestClient restClient;
    private final String apiKey;
    private final String instance;

    EvolutionApiSender(RestClient restClient, String apiKey, String instance) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.instance = instance;
    }

    @Override
    public void send(String phone, String message) {
        try {
            restClient.post()
                    .uri("/message/sendText/{instance}", instance)
                    .header("apikey", apiKey)
                    .body(new SendTextRequest(digitsOf(phone), message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // Sem telefone nem mensagem aqui: a exceção vai para o log.
            throw new WhatsAppDeliveryException("Evolution API não entregou a mensagem", e);
        }
    }

    /**
     * O cadastro guarda telefone em E.164, com o "+" ({@code +5511999990000});
     * a Evolution documenta o campo {@code number} em dígitos puros
     * ({@code 5511999990000}), que é o formato do JID do WhatsApp.
     *
     * Traduzir é trabalho do adapter, não do domínio: E.164 é a representação
     * certa para guardar, e cada provedor que queira outra coisa converte na
     * borda. Há relato de que a própria Evolution tolera o "+", mas depender de
     * tolerância não documentada para a única saída externa do sistema é apostar
     * onde não se pode perder.
     */
    private static String digitsOf(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }

    private record SendTextRequest(String number, String text) {
    }
}
