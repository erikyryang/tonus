package br.com.tonus.shared;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity to have changes recorded in audit_log.
 * The listener that actually writes the records doesn't exist yet
 * (GREEN phase) — for now the annotation only declares the contract.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Audited {
}
