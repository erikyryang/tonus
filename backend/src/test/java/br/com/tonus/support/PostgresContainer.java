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

    public static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16");

    static {
        INSTANCE.start();
    }

    private PostgresContainer() {
    }
}
