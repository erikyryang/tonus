/**
 * Autenticação e autorização dos três papéis.
 *
 * Depende de registration porque é dono da tabela users, que carrega o vínculo
 * com aluno/professor: valida o vínculo na criação e escuta os eventos de
 * desativação para revogar acesso de quem saiu do estúdio. A aresta é acíclica
 * — registration não conhece auth.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "registration"})
package br.com.tonus.auth;
