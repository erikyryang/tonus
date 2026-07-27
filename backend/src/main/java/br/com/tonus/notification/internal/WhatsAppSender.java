package br.com.tonus.notification.internal;

/**
 * Porta de saída para WhatsApp. Trocar de provedor é escrever outro adapter;
 * os listeners não mudam.
 *
 * Fica em internal/, e não na raiz do módulo: a raiz é contrato para outros
 * módulos, e nenhum módulo chama notification — ele só escuta. Expor a porta
 * lá fora convidaria exatamente o que o projeto proíbe, que é outro módulo
 * mandar WhatsApp direto em vez de publicar evento.
 */
public interface WhatsAppSender {

    /**
     * Entrega a mensagem, ou levanta {@link WhatsAppDeliveryException}. Não
     * tem retorno de sucesso/falha de propósito: o chamador precisa que a
     * falha derrube a transação, e valor de retorno ignorado por engano é
     * silêncio onde deveria haver reprocessamento.
     */
    void send(String phone, String message);
}
