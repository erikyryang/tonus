package br.com.tonus.auth.internal;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Limita tentativas de login por identificador (login informado), não por
 * IP — isola cenários de teste/usuários diferentes e evita que uma conta
 * sob ataque consuma o limite de outra.
 */
@Component
public class LoginRateLimiter {

    private static final int CAPACITY = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /**
     * Teto do próprio mapa de buckets, não do rate limit em si.
     *
     * /auth/login é público e aceita qualquer valor de {@code login} — sem
     * teto, cada valor distinto vira uma entrada que nunca é removida, e um
     * atacante sem credencial nenhuma esgota a heap da instância só variando
     * essa string a cada requisição. LinkedHashMap em modo de acesso (o
     * {@code true} do construtor) descarta a entrada menos recentemente usada
     * quando o mapa passa do teto: quem está de fato tentando logar continua
     * dentro da janela de um minuto e nunca é o descartado.
     *
     * Acesso é síncrono de propósito: bucket4j já serializa a decisão dentro
     * de cada bucket, e o mapa em si só precisa de exclusão mútua na hora de
     * criar ou promover uma entrada — não é o caminho quente de uma aplicação
     * deste tamanho.
     */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Bucket> buckets = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
            return size() > MAX_TRACKED_KEYS;
        }
    };

    public synchronized boolean tryConsume(String key) {
        return buckets.computeIfAbsent(key, k -> newBucket()).tryConsume(1);
    }

    /** Só para o teste do teto — nada em produção consulta o tamanho do mapa. */
    synchronized int trackedKeyCount() {
        return buckets.size();
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(CAPACITY, Refill.greedy(CAPACITY, WINDOW));
        return Bucket.builder().addLimit(limit).build();
    }
}
