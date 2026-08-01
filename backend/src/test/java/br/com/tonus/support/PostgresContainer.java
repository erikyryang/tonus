package br.com.tonus.support;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Container único de Postgres para toda a JVM de teste (padrão "singleton
 * container"): iniciado uma vez no static initializer, nunca parado
 * explicitamente — o Ryuk derruba ao fim da JVM.
 *
 * Vive aqui, e não dentro de uma classe base de teste, porque há mais de um
 * ponto de entrada: os testes de integração via HTTP e os
 * {@code @ApplicationModuleTest}, que não compartilham hierarquia. Cada um
 * declarando o seu subiria um container por bootstrap.
 *
 * Usar {@code @Testcontainers}/{@code @Container} aqui reiniciaria o container
 * a cada classe e deixaria o Spring com um ApplicationContext em cache
 * apontando para uma porta já morta.
 */
public final class PostgresContainer {

    /**
     * O {@code max_connections} sobe porque o gargalo é do servidor, não dos
     * pools. O Spring cacheia um ApplicationContext por combinação de
     * configuração de teste, e cada um abre o seu Hikari de 10 conexões — com o
     * default de 100 do Postgres, o décimo contexto a subir falha com "sorry,
     * too many clients already", e falha numa classe qualquer da suíte, sem
     * relação com a que acrescentou o contexto.
     *
     * Apertar o pool em vez disso seria pior: os listeners assíncronos do
     * Modulith tomam conexão em paralelo com a thread do teste, e um pool curto
     * troca o erro de subida por timeout de conexão no meio do teste.
     */
    public static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16")
            .withCommand("postgres", "-c", "max_connections=300");

    static {
        INSTANCE.start();
    }

    private PostgresContainer() {
    }
}
