/**
 * Cross-cutting infrastructure: errors (Problem Details), Clock, auditing,
 * Jackson config.
 *
 * Does not depend on any other module — it's the base that all the others
 * rest on. The empty list is the declaration of that, and ArchitectureTest
 * verifies it.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package br.com.tonus.shared;
