package br.com.tonus.notification.internal;

/**
 * A mensagem não saiu.
 *
 * É exceção de runtime para derrubar a transação do listener: o registro em
 * notification_log volta atrás e a publicação fica com completion_date nulo,
 * que é o que o registry do Modulith reprocessa depois. Tratar a falha em vez
 * de propagá-la marcaria o evento como entregue sem que ninguém tivesse sido
 * avisado.
 */
public class WhatsAppDeliveryException extends RuntimeException {

    public WhatsAppDeliveryException(String message) {
        super(message);
    }

    public WhatsAppDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
