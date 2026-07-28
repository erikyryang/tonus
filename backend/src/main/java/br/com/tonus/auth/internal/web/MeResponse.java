package br.com.tonus.auth.internal.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Quem está logado, do ponto de vista do front.
 *
 * {@code studentId} e {@code teacherId} saem da resposta quando nulos: o
 * ADMIN não é nem aluno nem professor, e mandar {@code null} obrigaria o
 * cliente a distinguir "ausente" de "vazio" sem ganho nenhum.
 *
 * {@code name} é o login do usuário. O nome civil mora em registration, e
 * consultar registration a cada verificação de sessão acoplaria o caminho mais
 * quente da API a outro módulo — ver SPEC-006.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeResponse(String role, String name, Long studentId, Long teacherId) {
}
