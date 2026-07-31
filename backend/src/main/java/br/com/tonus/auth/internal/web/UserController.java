package br.com.tonus.auth.internal.web;

import br.com.tonus.auth.internal.AuthService;
import br.com.tonus.auth.internal.LoginAlreadyTakenException;
import br.com.tonus.auth.internal.UnknownLinkedPersonException;
import br.com.tonus.auth.internal.UserNotFoundException;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Criação do usuário de acesso do aluno/professor pelo ADMIN (SPEC-002).
 * Mora no auth porque é o auth que é dono da tabela users; o vínculo é validado
 * contra o cadastro via RegistrationFacade.
 *
 * Não existe rota de desativação: o acesso cai sozinho quando o aluno/professor
 * é desativado no cadastro, por evento. A reconcessão é que é manual.
 */
@RestController
@RequestMapping("/users")
class UserController {

    /**
     * Uma página de alunos com folga larga. O cliente pergunta pela página que
     * o próprio backend acabou de devolver, e nenhuma listagem chega perto
     * disso — o teto é contra o uso errado, não contra o uso previsto.
     */
    private static final int MAX_STUDENT_IDS = 200;

    private final AuthService authService;

    UserController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = UserResponse.from(authService.createUser(
                request.login(), request.password(), request.role(), request.studentId(), request.teacherId()));
        return ResponseEntity.created(URI.create("/users/" + response.id())).body(response);
    }

    /**
     * Quem já tem login, entre estes alunos. É a consulta que permite à tela
     * distinguir "criar acesso" de "reativar acesso" — e sem ela o id do
     * usuário, que a reativação exige, nunca chegaria ao cliente.
     *
     * Em lote e não por aluno: a listagem mostra uma página inteira de alunos
     * de cada vez, e uma chamada por linha seria absurdo.
     *
     * Não é paginado, diferente das outras listagens, e o teto abaixo é o que
     * torna isso defensável: a regra da raiz existe para que nenhuma resposta
     * cresça sem limite, e aqui o limite está na pergunta — no máximo um
     * usuário por aluno informado, no máximo {@value #MAX_STUDENT_IDS} alunos.
     * Sem o teto a promessa dependeria de boa-fé do cliente, e a lista vira
     * cláusula IN de um parâmetro por id: o suficiente para estourar o limite
     * do driver e virar 500 no lugar de um 400 honesto.
     */
    @GetMapping
    List<UserResponse> byStudents(@RequestParam List<Long> studentIds) {
        if (studentIds.size() > MAX_STUDENT_IDS) {
            throw new TooManyStudentIdsException(studentIds.size(), MAX_STUDENT_IDS);
        }
        return authService.findUsersOfStudents(studentIds).stream().map(UserResponse::from).toList();
    }

    @PatchMapping("/{id}/activate")
    UserResponse activate(@PathVariable Long id) {
        return UserResponse.from(authService.activateUser(id));
    }

    @ExceptionHandler(LoginAlreadyTakenException.class)
    ProblemDetail handleLoginAlreadyTaken(LoginAlreadyTakenException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(UnknownLinkedPersonException.class)
    ProblemDetail handleUnknownLinkedPerson(UnknownLinkedPersonException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleUserNotFound() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Usuário não encontrado");
    }

    @ExceptionHandler(TooManyStudentIdsException.class)
    ProblemDetail handleTooManyStudentIds(TooManyStudentIdsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Pedido grande demais é erro de uso do cliente, e 400 é o que diz isso. */
    static class TooManyStudentIdsException extends RuntimeException {

        TooManyStudentIdsException(int asked, int max) {
            super("A consulta aceita no máximo %d alunos por vez; vieram %d".formatted(max, asked));
        }
    }
}
