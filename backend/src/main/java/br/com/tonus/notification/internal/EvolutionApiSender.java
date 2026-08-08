package br.com.tonus.notification.internal;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Evolution API adapter.
 *
 * Translates any HTTP failure into WhatsAppDeliveryException. The
 * translation is the point: the caller needs to distinguish "didn't
 * deliver" from a programming error, because only the former should roll
 * back the transaction and come back through reprocessing.
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
            // No phone or message here: the exception goes to the log.
            throw new WhatsAppDeliveryException("Evolution API não entregou a mensagem", e);
        }
    }

    /**
     * Registration stores the phone in E.164, with the "+" ({@code
     * +5511999990000}); Evolution documents the {@code number} field in
     * plain digits ({@code 5511999990000}), which is the WhatsApp JID
     * format.
     *
     * Translating is the adapter's job, not the domain's: E.164 is the right
     * representation to store, and each provider that wants something else
     * converts at the edge. There are reports that Evolution itself
     * tolerates the "+", but relying on undocumented tolerance for the
     * system's only external output is betting where you can't afford to
     * lose.
     */
    private static String digitsOf(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }

    private record SendTextRequest(String number, String text) {
    }
}
