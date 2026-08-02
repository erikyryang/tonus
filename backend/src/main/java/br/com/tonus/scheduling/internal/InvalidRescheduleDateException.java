package br.com.tonus.scheduling.internal;

/**
 * A data de destino de uma remarcação precisa ser um dia em que a aula possa
 * de fato acontecer: não no passado e não num feriado já cadastrado.
 *
 * Nenhuma das duas é constraint de banco, e nem poderia ser — as duas dependem
 * de estado externo à linha (a data de hoje, a tabela announcement).
 */
public class InvalidRescheduleDateException extends RuntimeException {

    public InvalidRescheduleDateException(String message) {
        super(message);
    }
}
