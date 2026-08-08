/**
 * Authentication and authorization for the three roles.
 *
 * Depends on registration because it owns the users table, which carries the
 * link to student/teacher: it validates the link on creation and listens to
 * deactivation events to revoke access for whoever left the studio. The edge
 * is acyclic — registration doesn't know about auth.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "registration"})
package br.com.tonus.auth;
