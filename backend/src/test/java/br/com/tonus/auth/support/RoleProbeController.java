package br.com.tonus.auth.support;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller que só existe em teste, para isolar a verificação de que o
 * pipeline de autenticação/autorização (cookie JWT -> SecurityContext com o
 * role correto) funciona, sem depender de endpoints de negócio de outros
 * módulos ainda não implementados.
 */
@RestController
public class RoleProbeController {

    @GetMapping("/test/probe/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminOnly() {
        return "ok";
    }

    @GetMapping("/test/probe/teacher-only")
    @PreAuthorize("hasRole('TEACHER')")
    public String teacherOnly() {
        return "ok";
    }

    @GetMapping("/test/probe/student-only")
    @PreAuthorize("hasRole('STUDENT')")
    public String studentOnly() {
        return "ok";
    }
}
