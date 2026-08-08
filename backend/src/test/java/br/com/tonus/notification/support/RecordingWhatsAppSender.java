package br.com.tonus.notification.support;

import br.com.tonus.notification.internal.WhatsAppDeliveryException;
import br.com.tonus.notification.internal.WhatsAppSender;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fake for the only point where the system talks to the external world.
 *
 * Replacing the port is what lets the no-duplication rule be tested without
 * depending on the Evolution API — and it is the right fake: what is being
 * exercised is the listener, not the HTTP. The real adapter has its own test
 * (EvolutionApiSenderTest, with MockRestServiceServer).
 *
 * Hand-made instead of Mockito because the "fail for this phone" weapon needs
 * to hold across threads: {@code @ApplicationModuleListener} is asynchronous,
 * so who records and who reads the result are not the same thread.
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

    /** From now on, every send to this phone fails. */
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
     * The last message that reached a phone. The fan-out notifies every active
     * student in the database, so {@code sent().getLast()} may belong to a
     * student another class left behind — whoever wants to check the text has
     * to say whose it is.
     */
    public String lastMessageTo(String phone) {
        return sent.stream()
                .filter(s -> s.phone().equals(phone))
                .map(Sent::message)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    /**
     * Attempts, including the failed ones. It is the only signal that the
     * listener got to run when the send breaks — the database record is undone
     * by the rollback, so counting rows does not serve to wait for that case.
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
