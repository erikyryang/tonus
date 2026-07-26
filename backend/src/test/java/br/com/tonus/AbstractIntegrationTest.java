package br.com.tonus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /**
     * Container único para toda a JVM de teste (padrão "singleton container"
     * do Testcontainers): iniciado uma vez no static initializer, nunca
     * parado explicitamente (o Ryuk derruba ao fim da JVM). Usar
     * {@code @Testcontainers}/{@code @Container} aqui reiniciaria o container
     * a cada classe de teste e deixava o Spring com um ApplicationContext em
     * cache apontando pra uma porta antiga já morta.
     */
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;
}
