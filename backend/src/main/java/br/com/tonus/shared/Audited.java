package br.com.tonus.shared;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca uma entidade para ter mudanças registradas em audit_log.
 * O listener que efetivamente grava os registros ainda não existe
 * (fase GREEN) — por enquanto a anotação só declara o contrato.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Audited {
}
