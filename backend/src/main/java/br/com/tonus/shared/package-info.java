/**
 * Infra transversal: erros (Problem Details), Clock, auditoria, config Jackson.
 *
 * Não depende de nenhum outro módulo — é a base sobre a qual todos os outros se
 * apoiam. A lista vazia é a declaração disso, e o ArchitectureTest a verifica.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package br.com.tonus.shared;
