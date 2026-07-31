package br.com.tonus.auth.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * {@code @EnableMethodSecurity} continua ligado mesmo sem nenhum
 * {@code @PreAuthorize} no projeto: desligá-lo faria uma anotação futura virar
 * um no-op silencioso, que é o pior desfecho possível para autorização. Ligado,
 * ela funciona — e o teste de arquitetura é que reprova o uso em controller.
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
                 * O inventário de quem pode chamar o quê — em um lugar só, e não
                 * em @PreAuthorize espalhado pelos controllers.
                 *
                 * Autorização por anotação funciona, e é justamente por isso que
                 * engana: para responder "quem enxerga a agenda do estúdio?" é
                 * preciso abrir todos os controllers e confiar que nenhum foi
                 * esquecido — e o esquecido não falha, ele libera. Aqui a lista
                 * é legível de uma vez, e a última regra vale para o que não foi
                 * previsto. Um teste (AuthorizationLivesInSecurityConfigIT)
                 * impede o padrão de voltar.
                 *
                 * A ordem importa: vale a primeira regra que casar.
                 */
                .authorizeHttpRequests(auth -> auth
                        // Entrar, renovar e sair acontecem sem sessão válida.
                        .requestMatchers("/auth/login", "/auth/refresh", "/auth/logout").permitAll()

                        // Endpoints /me: o papel decide o acesso; o id do aluno
                        // ou do professor vem do token, nunca do path.
                        //
                        // Cada caminho é listado inteiro, sem /**: foi um teste
                        // pedindo 403 e recebendo 404 que mostrou o risco do
                        // contrário — /me/schedule não cobre /me/schedule/week,
                        // e o endpoint novo cairia no anyRequest() abaixo,
                        // aberto a qualquer papel autenticado.
                        .requestMatchers("/me/schedule", "/me/schedule/week", "/me/invoices").hasRole("STUDENT")
                        .requestMatchers("/me/grid").hasRole("TEACHER")

                        // Cadastro e acesso.
                        .requestMatchers("/users/**", "/students/**", "/teachers/**").hasRole("ADMIN")

                        // Agenda: quem configura é o ADMIN. O professor é dono da
                        // aula, mas não decide horário nem feriado — deixá-lo
                        // escrever aqui lhe daria poder sobre alunos de outro.
                        .requestMatchers("/schedule/**", "/schedule-exceptions/**",
                                "/default-schedules/**", "/holidays/**").hasRole("ADMIN")

                        // Cobrança.
                        .requestMatchers("/subscriptions/**", "/invoices/**").hasRole("ADMIN")

                        // /auth/me e o que mais existir: exige sessão, sem
                        // escolher papel. Mesma regra de antes desta mudança —
                        // endpoint novo continua nascendo autenticado, e é a
                        // linha acima que precisa ganhar a exceção.
                        .anyRequest().authenticated())
                // Não autenticado é 401; autenticado sem permissão continua 403.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new ProblemAuthenticationEntryPoint(objectMapper)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
