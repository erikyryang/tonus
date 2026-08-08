package br.com.tonus.auth.internal;

import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * {@code @EnableMethodSecurity} stays enabled even without a single
 * {@code @PreAuthorize} in the project: turning it off would make a future
 * annotation become a silent no-op, which is the worst possible outcome for
 * authorization. Enabled, it works — and the architecture test is what fails
 * on its use in a controller.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    JwtService jwtService,
                                                    ObjectMapper objectMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                /*
                 * The inventory of who can call what — in one single place,
                 * not scattered across controllers as @PreAuthorize.
                 *
                 * Annotation-based authorization works, and that's exactly why
                 * it's deceptive: answering "who can see the studio's
                 * schedule?" requires opening every controller and trusting
                 * that none was forgotten — and the forgotten one doesn't
                 * fail, it lets through. Here the list is readable in one
                 * pass, and the last rule covers whatever wasn't foreseen. A
                 * test (AuthorizationLivesInSecurityConfigIT) keeps the
                 * pattern from coming back.
                 *
                 * Order matters: the first rule that matches wins.
                 */
                .authorizeHttpRequests(auth -> auth
                        // Logging in, refreshing and logging out happen without a valid session.
                        .requestMatchers("/auth/login", "/auth/refresh", "/auth/logout").permitAll()

                        // /me endpoints: the role decides access; the student's
                        // or teacher's id comes from the token, never from the path.
                        //
                        // Each path is listed in full, without /**: it was a test
                        // expecting 403 and getting 404 that showed the risk of the
                        // opposite — /me/schedule doesn't cover /me/schedule/week,
                        // and a new endpoint would fall into the anyRequest() below,
                        // open to any authenticated role.
                        .requestMatchers("/me/schedule", "/me/schedule/week", "/me/invoices").hasRole("STUDENT")
                        .requestMatchers("/me/grid").hasRole("TEACHER")

                        // Registration and access.
                        .requestMatchers("/users/**", "/students/**", "/teachers/**").hasRole("ADMIN")

                        // Schedule: only the ADMIN configures it. The teacher owns
                        // the class, but doesn't decide schedule or holidays —
                        // letting them write here would give them power over
                        // another teacher's students.
                        .requestMatchers("/schedule/**", "/schedule-exceptions/**",
                                "/default-schedules/**", "/announcements/**").hasRole("ADMIN")

                        // Billing.
                        .requestMatchers("/subscriptions/**", "/invoices/**", "/plans/**",
                                "/plan-enrollments/**", "/billing-settings").hasRole("ADMIN")

                        // /auth/me and whatever else exists: requires a session,
                        // without choosing a role. Same rule as before this change —
                        // a new endpoint is still born authenticated, and it's the
                        // line above that needs to gain the exception.
                        .anyRequest().authenticated())
                // Not authenticated is 401; authenticated without permission stays 403.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new ProblemAuthenticationEntryPoint(objectMapper)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
