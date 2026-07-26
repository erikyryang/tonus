package br.com.tonus.registration;

/**
 * Dados de contato de um aluno. O telefone é dado pessoal: quem consumir nunca
 * deve logá-lo por inteiro.
 */
public record StudentContact(Long studentId, String name, String phone) {
}
