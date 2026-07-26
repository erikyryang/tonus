/**
 * Agenda: horário padrão, exceções pontuais e feriados.
 *
 * Depende de registration porque a agenda não existe sem aluno — e o professor
 * de cada aula sai do vínculo do aluno, nunca de um campo próprio daqui.
 *
 * A aresta é acíclica: registration não conhece scheduling. Efeito colateral
 * para fora do módulo é evento (ClassCanceled, ClassRescheduled,
 * HolidayCreated), nunca chamada direta.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "registration"})
package br.com.tonus.scheduling;
