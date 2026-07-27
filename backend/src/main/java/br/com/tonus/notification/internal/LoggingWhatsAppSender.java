package br.com.tonus.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter usado quando a integração está desligada: registra que houve envio e
 * não fala com ninguém.
 *
 * É o que permite subir a aplicação em desenvolvimento sem uma sessão de
 * WhatsApp de verdade. Nunca falha, de propósito — simular indisponibilidade
 * aqui esconderia o caminho feliz do resto do sistema.
 */
class LoggingWhatsAppSender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingWhatsAppSender.class);

    LoggingWhatsAppSender() {
        // Em nível de aviso, e na subida: ninguém percebe que os avisos não
        // estão saindo até um aluno reclamar que não soube do cancelamento.
        log.warn("notification.evolution.enabled=false — nenhum WhatsApp será enviado");
    }

    @Override
    public void send(String phone, String message) {
        log.info("Envio de WhatsApp desabilitado; mensagem para {} não foi enviada", mask(phone));
    }

    /**
     * Só os quatro últimos dígitos. Telefone completo em log é dado pessoal
     * vazando para onde ninguém controla retenção.
     *
     * Visível no pacote para ter teste próprio: é regra de privacidade, e o
     * que a garante não deve depender de alguém inspecionar log à mão.
     */
    static String mask(String phone) {
        if (phone == null || phone.length() <= 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }
}
