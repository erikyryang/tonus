package br.com.tonus.auth.internal;

import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

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
                /*
                 * CSRF over a stateless cookie session (see
                 * CookieCsrfTokenRepository): the session lives in the
                 * access_token cookie, the browser sends it on its own, and
                 * same-site boundaries are the only thing a cross-site request
                 * must not cross. The XSRF token is the second wall: a
                 * cross-site request that somehow carries the cookie (older
                 * browser, bypassed SameSite) still cannot forge the header,
                 * because it cannot read the cookie's value.
                 *
                 * The denial goes through the exceptionHandling
                 * accessDeniedHandler below — Spring Security wires it into
                 * the CSRF filter on its own — so a missing token is a 403
                 * Problem Details written straight to the response, not a
                 * sendError that the container re-dispatches into a 401.
                 */
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(csrfRequestHandler())
                        /*
                         * Only login is exempt, and only because it is the
                         * cold start: no cookie exists to echo yet. It is also
                         * the least exposed of the trio — it demands a JSON
                         * body, and a cross-site form cannot set that header.
                         *
                         * Refresh and logout are protected like everything
                         * else: refresh mints new access tokens, and the
                         * httpOnly SameSite=Strict refresh cookie is not the
                         * only wall it needs. The XSRF cookie is persistent
                         * (see csrfTokenRepository) for the same reason the
                         * refresh cookie is: after a browser restart the
                         * session must renew itself without a fresh login.
                         */
                        .ignoringRequestMatchers("/auth/login"))
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
                                "/plan-enrollments/**").hasRole("ADMIN")

                        // WhatsApp gateway: the session belongs to the studio,
                        // not to a role that has students. A teacher who could
                        // disconnect it would silence every notice.
                        .requestMatchers("/whatsapp/**").hasRole("ADMIN")

                        // /auth/me and whatever else exists: requires a session,
                        // without choosing a role. Same rule as before this change —
                        // a new endpoint is still born authenticated, and it's the
                        // line above that needs to gain the exception.
                        .anyRequest().authenticated())
                // Not authenticated is 401; authenticated without permission is 403.
                //
                // Both handlers write the response themselves instead of calling
                // sendError. That is not style: sendError makes the container
                // re-dispatch to /error, the chain filters ERROR dispatches too,
                // and the stateless context is gone by then — so the 403 came
                // back as 401. Covered by AuthorizationStatusIT, which runs on a
                // real container because MockMvc does not re-dispatch and
                // therefore never saw it.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new ProblemAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new ProblemAccessDeniedHandler(objectMapper)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * The token rides in an {@code XSRF-TOKEN} cookie that the front can read
     * (non-httpOnly, by design) and echoes back as an {@code X-XSRF-TOKEN}
     * header — the double-submit pattern: the value is not a secret, what
     * protects it is that a cross-site page cannot read it (same-origin
     * policy), so it cannot forge the header.
     *
     * {@code SameSite=Strict} matches the auth cookies: a cross-site request
     * does not even carry the cookie. {@code Secure} follows the request's
     * transport, like {@code AuthController.buildCookie}.
     *
     * The cookie is persistent for as long as the refresh token: a browser
     * restart drops session cookies but not the seven-day ones, and the
     * session must be able to renew (POST /auth/refresh) with the token still
     * in hand — the XSRF cookie and the refresh cookie are born together at
     * login and die together.
     */
    private static CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(builder -> builder
                .sameSite("Strict")
                .maxAge(Duration.ofDays(7)));
        return repository;
    }

    /**
     * The default {@code XorCsrfTokenRequestAttributeHandler} masks the token
     * (BREACH protection) so the value the server expects in the header is not
     * the raw cookie value — the front would have to decode it. The plain
     * handler accepts the cookie value as-is, which is what an echo does.
     *
     * Setting the attribute name to null forces the token to be materialized
     * on every request, login included: otherwise the cookie only appears on
     * the first protected call, and the front would have no token to echo.
     */
    private static CsrfTokenRequestAttributeHandler csrfRequestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }
}
