package br.com.tonus.auth.support;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller that only exists in tests, to isolate verification that the
 * authentication/authorization pipeline (JWT cookie -> SecurityContext with
 * the correct role) works, without depending on business endpoints from
 * other modules that aren't implemented yet.
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
