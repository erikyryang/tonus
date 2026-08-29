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
 * Creation of the student/teacher access user by the ADMIN (SPEC-002). Lives
 * in auth because auth owns the users table; the link is validated against
 * registration via RegistrationFacade.
 *
 * There's no deactivation route: access drops on its own, by event, when the
 * student/teacher is deactivated in registration. It's regranting that's
 * manual.
 */
@RestController
@RequestMapping("/users")
class UserController {

    /**
     * A page of students with wide margin to spare. The client asks about the
     * page that the backend itself just returned, and no listing comes close
     * to this — the cap is against misuse, not against expected use.
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
     * Who already has a login, among these students. It's the query that lets
     * the screen distinguish "create access" from "reactivate access" — and
     * without it the user id, which reactivation requires, would never reach
     * the client.
     *
     * Batched, not per student: the listing shows a whole page of students at
     * a time, and one call per row would be absurd.
     *
     * Not paginated, unlike the other listings, and the cap below is what
     * makes that defensible: the root rule exists so that no response grows
     * unbounded, and here the bound is in the question itself — at most one
     * user per student id given, at most {@value #MAX_STUDENT_IDS} students.
     * Without the cap the promise would rely on the client's good faith, and
     * the list turns into an IN clause with one parameter per id: enough to
     * blow past the driver's limit and become a 500 instead of an honest 400.
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

    /**
     * Safety net for the same race billing guards in {@code BillingExceptionHandler}:
     * the pre-check in {@code AuthService.createUser} has a window, and a concurrent
     * request can win the UNIQUE constraint first — without this handler that
     * surfaces as a 500 instead of the 409 the endpoint already knows how to say.
     */
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    ProblemDetail handleConcurrentDuplicate() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Login já em uso");
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

    /** A request that's too large is a client misuse error, and 400 is what says so. */
    static class TooManyStudentIdsException extends RuntimeException {

        TooManyStudentIdsException(int asked, int max) {
            super("A consulta aceita no máximo %d alunos por vez; vieram %d".formatted(max, asked));
        }
    }
}
