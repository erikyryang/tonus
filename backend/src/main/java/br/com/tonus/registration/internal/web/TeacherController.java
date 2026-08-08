package br.com.tonus.registration.internal.web;

import br.com.tonus.registration.internal.RegistrationService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
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
 * There is no DELETE: leaving the studio is deactivation, so as not to erase invoice
 * history (SPEC-002).
 */
@RestController
@RequestMapping("/teachers")
class TeacherController {

    private final RegistrationService registrationService;

    TeacherController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping
    ResponseEntity<TeacherResponse> create(@Valid @RequestBody TeacherRequest request) {
        TeacherResponse response = TeacherResponse.from(
                registrationService.createTeacher(request.name(), request.phone()));
        return ResponseEntity.created(URI.create("/teachers/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    TeacherResponse get(@PathVariable Long id) {
        return TeacherResponse.from(registrationService.findTeacher(id));
    }

    @GetMapping
    Page<TeacherResponse> list(@RequestParam(required = false) Boolean active,
                               @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return registrationService.findTeachers(active, pageable).map(TeacherResponse::from);
    }

    @PutMapping("/{id}")
    TeacherResponse update(@PathVariable Long id, @Valid @RequestBody TeacherRequest request) {
        return TeacherResponse.from(registrationService.updateTeacher(id, request.name(), request.phone()));
    }

    @PatchMapping("/{id}/deactivate")
    TeacherResponse deactivate(@PathVariable Long id) {
        return TeacherResponse.from(registrationService.deactivateTeacher(id));
    }

    @PatchMapping("/{id}/activate")
    TeacherResponse activate(@PathVariable Long id) {
        return TeacherResponse.from(registrationService.activateTeacher(id));
    }
}
