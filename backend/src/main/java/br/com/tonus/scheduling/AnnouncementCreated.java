package br.com.tonus.scheduling;

import java.time.LocalDate;

/**
 * Comunicado cadastrado. O consumidor avisa todo aluno ativo — quem são eles é
 * decisão de quem consome, não deste evento.
 *
 * Carrega a descrição e o efeito porque a mensagem depende dos dois: "não
 * teremos aula" e "as aulas acontecem normalmente" são avisos diferentes, e o
 * motivo é o que faz o aviso valer alguma coisa. Sem eles o consumidor teria de
 * consultar a agenda para montar o texto — e `notification` não pergunta nada a
 * `scheduling`, por decisão de arquitetura.
 */
public record AnnouncementCreated(LocalDate date, String description, boolean suspendsClasses) {
}
