package br.com.tonus.registration.internal.web;

import br.com.tonus.registration.internal.RegistrationService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Não existe DELETE: saída do estúdio é desativação, para não apagar histórico
 * de fatura (SPEC-002).
 */
@RestController
@RequestMapping("/students")
@PreAuthorize("hasRole('ADMIN')")
class StudentController {

    private final RegistrationService registrationService;

    StudentController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping
    ResponseEntity<StudentResponse> create(@Valid @RequestBody StudentRequest request) {
        StudentResponse response = StudentResponse.from(
                registrationService.createStudent(request.name(), request.phone(), request.teacherId()));
        return ResponseEntity.created(URI.create("/students/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    StudentResponse get(@PathVariable Long id) {
        return StudentResponse.from(registrationService.findStudent(id));
    }

    @GetMapping
    Page<StudentResponse> list(@RequestParam(required = false) Boolean active,
                               @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return registrationService.findStudents(active, pageable).map(StudentResponse::from);
    }

    @PutMapping("/{id}")
    StudentResponse update(@PathVariable Long id, @Valid @RequestBody StudentRequest request) {
        return StudentResponse.from(
                registrationService.updateStudent(id, request.name(), request.phone(), request.teacherId()));
    }

    @PatchMapping("/{id}/deactivate")
    StudentResponse deactivate(@PathVariable Long id) {
        return StudentResponse.from(registrationService.deactivateStudent(id));
    }

    @PatchMapping("/{id}/activate")
    StudentResponse activate(@PathVariable Long id) {
        return StudentResponse.from(registrationService.activateStudent(id));
    }
}
