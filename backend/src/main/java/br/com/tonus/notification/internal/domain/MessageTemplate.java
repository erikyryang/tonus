package br.com.tonus.notification.internal.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * O que se manda ao aluno, e o nome pelo qual esse aviso é reconhecido no
 * banco.
 *
 * O nome da constante é identificador e fica em inglês; o corpo, que o aluno
 * lê, é português — é a distinção da decisão de idioma do projeto.
 *
 * Toda mensagem começa pelo nome do aluno, e por isso {@link #render} recebe o
 * nome separado dos demais argumentos: quem chama não tem como esquecer dele
 * nem trocar a ordem.
 */
public enum MessageTemplate {

    CLASS_CANCELED("Olá, %s! Sua aula do dia %s foi cancelada."),
    CLASS_RESCHEDULED("Olá, %s! Sua aula do dia %s foi remarcada para %s às %s."),
    HOLIDAY_CREATED("Olá, %s! Dia %s é feriado e não teremos aula."),
    INVOICE_DUE_SOON("Olá, %s! Sua mensalidade de %s vence em %s."),
    INVOICE_OVERDUE("Olá, %s! Sua mensalidade com vencimento em %s está em aberto."),
    PAYMENT_REGISTERED("Olá, %s! Recebemos seu pagamento de %s em %s. Obrigado!");

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
