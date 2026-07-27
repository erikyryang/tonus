package br.com.tonus.notification.support;

import br.com.tonus.notification.internal.WhatsAppDeliveryException;
import br.com.tonus.notification.internal.WhatsAppSender;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dublê do único ponto em que o sistema fala com o mundo externo.
 *
 * Substituir a porta é o que permite testar a regra de não-duplicação sem
 * depender da Evolution API — e é o dublê certo: o que se está exercitando é o
 * listener, não o HTTP. O adapter de verdade tem teste próprio
 * (EvolutionApiSenderTest, com MockRestServiceServer).
 *
 * Feito à mão em vez de Mockito porque a arma "falhe para este telefone"
 * precisa valer entre threads: {@code @ApplicationModuleListener} é assíncrono,
 * então quem grava e quem lê o resultado não são a mesma thread.
 */
public class RecordingWhatsAppSender implements WhatsAppSender {

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private final List<String> attempted = new CopyOnWriteArrayList<>();
    private final Set<String> failingPhones = ConcurrentHashMap.newKeySet();

    @Override
    public void send(String phone, String message) {
        attempted.add(phone);
        if (failingPhones.contains(phone)) {
            throw new WhatsAppDeliveryException("Evolution indisponível (simulado no teste)");
        }
        sent.add(new Sent(phone, message));
    }

    /** A partir daqui, todo envio para este telefone falha. */
    public void failFor(String phone) {
        failingPhones.add(phone);
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    public long countSentTo(String phone) {
        return sent.stream().filter(s -> s.phone().equals(phone)).count();
    }

    /**
     * Tentativas, inclusive as que falharam. É o único sinal de que o listener
     * chegou a rodar quando o envio quebra — o registro em banco é desfeito
     * pelo rollback, então contar linha não serve para esperar por esse caso.
     */
    public long countAttemptsTo(String phone) {
        return attempted.stream().filter(phone::equals).count();
    }

    public List<String> attempts() {
        return List.copyOf(attempted);
    }

    public void reset() {
        sent.clear();
        attempted.clear();
        failingPhones.clear();
    }

    public record Sent(String phone, String message) {
    }
}
