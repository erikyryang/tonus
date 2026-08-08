package br.com.tonus.notification.internal.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * What gets sent to the student, and the name by which this notice is
 * recognized in the database.
 *
 * The constant's name is an identifier and stays in English; the body,
 * which the student reads, is Portuguese — that's the distinction from the
 * project's language decision.
 *
 * Every message starts with the student's name, and that's why {@link
 * #render} receives the name separate from the remaining arguments: the
 * caller has no way to forget it or swap the order.
 */
public enum MessageTemplate {

    CLASS_CANCELED("Olá, %s! Sua aula do dia %s foi cancelada."),
    CLASS_RESCHEDULED("Olá, %s! Sua aula do dia %s foi remarcada para %s às %s."),
    ANNOUNCEMENT_SUSPENSION("Olá, %s! Dia %s não teremos aula: %s."),
    ANNOUNCEMENT_NOTICE("Olá, %s! Aviso sobre o dia %s: %s. As aulas acontecem normalmente."),
    INVOICE_DUE_SOON("Olá, %s! Sua mensalidade de %s vence em %s."),
    INVOICE_OVERDUE("Olá, %s! Sua mensalidade com vencimento em %s está em aberto."),
    PAYMENT_REGISTERED("Olá, %s! Recebemos seu pagamento de %s em %s. Obrigado!"),
    /** Free-form ADMIN message (change 9): the text comes entirely from the trigger, with no format of its own. */
    BROADCAST("Olá, %s! %s");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final String format;

    MessageTemplate(String format) {
        this.format = format;
    }

    public String render(String studentName, Object... rest) {
        Object[] args = new Object[rest.length + 1];
        args[0] = studentName;
        System.arraycopy(rest, 0, args, 1, rest.length);
        return format.formatted(args);
    }

    public static String date(LocalDate date) {
        return date.format(DATE);
    }

    public static String time(LocalTime time) {
        return time.format(TIME);
    }

    public static String money(BigDecimal amount) {
        return "R$ " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
