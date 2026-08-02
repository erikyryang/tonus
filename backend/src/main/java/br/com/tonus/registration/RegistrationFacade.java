package br.com.tonus.registration;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contrato do módulo registration para os demais módulos (scheduling, billing,
 * notification). Consulta síncrona apenas — efeito colateral entre módulos é
 * por evento.
 */
public interface RegistrationFacade {

    Optional<StudentContact> findStudentContact(Long studentId);

    boolean studentExists(Long studentId);

    boolean teacherExists(Long teacherId);

    /**
     * Aluno existe e continua no estúdio. Separado de {@link #studentExists}
     * porque quem consulta precisa distinguir "não existe" de "saiu" — as duas
     * situações levam a mensagens e decisões diferentes.
     */
    boolean studentIsActive(Long studentId);

    boolean teacherIsActive(Long teacherId);

    /**
     * Nomes de vários alunos de uma vez, indexados por id.
     *
     * Existe para listagens que mostram aluno linha a linha (faturas, SPEC-008).
     * {@link #findStudentContact} não serve: seria uma consulta por linha e
     * entregaria telefone — dado pessoal — a quem só precisa do nome. Ids sem
     * aluno correspondente simplesmente não aparecem no mapa.
     *
     * Inclui aluno desativado de propósito: quem saiu do estúdio mantém
     * histórico de fatura (SPEC-002 não tem DELETE), e a tela continua tendo de
     * dizer de quem é a cobrança.
     */
    Map<Long, String> findStudentNames(Collection<Long> studentIds);

    /**
     * Telefones de vários alunos de uma vez, indexados por id.
     *
     * Existe separado de {@link #findStudentNames} porque telefone é dado
     * pessoal: quem só precisa do nome não deve recebê-lo de brinde. Quem chama
     * aqui declara a finalidade — a grade do professor, que atende aquele aluno.
     *
     * Em lote e não por aluno pela mesma razão dos nomes: a grade é percorrida
     * inteira de uma vez, e uma consulta por linha seria N+1.
     *
     * Nunca logar o valor devolvido.
     */
    Map<Long, String> findStudentPhones(Collection<Long> studentIds);

    /**
     * O dia em que o aluno entrou no estúdio.
     *
     * A cobrança precisa disso para fixar o vencimento da mensalidade no dia do
     * mês em que ele se cadastrou (SPEC-004). É data, não instante: quem
     * pergunta quer o dia do calendário no fuso do estúdio, e o horário do
     * cadastro não significa nada para uma mensalidade.
     *
     * Vazio quando o aluno não existe — a decisão do que fazer com isso é de
     * quem chama.
     */
    Optional<LocalDate> findRegistrationDate(Long studentId);

    /**
     * As datas de cadastro de vários alunos de uma vez, indexadas por id.
     *
     * Existe para o job diário de cobrança, que resolve o vencimento de cada
     * fatura pelo dia do cadastro (SPEC-004) e roda sobre o estúdio inteiro:
     * perguntar aluno a aluno seria N+1 no único lugar do sistema que trabalha
     * com todo mundo de uma vez. Ids sem aluno não aparecem no mapa.
     */
    Map<Long, LocalDate> findRegistrationDates(Collection<Long> studentIds);

    /**
     * Um aluno só, com nome e professor — o que a agenda precisa para montar a
     * linha de uma aula.
     *
     * Existe ao lado de {@link #findActiveStudents()} porque as perguntas são
     * diferentes: a agenda do estúdio quer todo mundo, a agenda de um aluno
     * quer um. Responder a segunda com a primeira significa carregar o estúdio
     * inteiro para descartar tudo menos uma linha, e é o aluno — no celular,
     * na tela que ele mais abre — quem paga por isso.
     *
     * Devolve o aluno inativo também: quem consulta é que decide o que fazer
     * com {@link StudentSummary#active()}, e some-lo silenciosamente
     * transformaria "saiu do estúdio" em "não existe".
     */
    Optional<StudentSummary> findStudentSummary(Long studentId);

    List<StudentSummary> findActiveStudents();

    List<StudentSummary> findStudentsByTeacher(Long teacherId);
}
