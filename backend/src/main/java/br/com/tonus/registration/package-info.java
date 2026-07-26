/**
 * Cadastro de alunos e professores.
 *
 * Depende só de shared. É o módulo mais consumido do sistema — scheduling,
 * billing e notification apontam para cá — então mantê-lo sem dependência de
 * negócio é o que evita ciclo mais adiante.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = "shared")
package br.com.tonus.registration;
