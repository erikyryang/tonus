package br.com.tonus.shared.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint que só existe em teste, para provocar uma exceção que nenhum módulo
 * mapeia. É a única forma de exercitar o handler de último recurso do shared —
 * o resto da API, por bem construída, nunca chega lá.
 */
@RestController
public class FailingProbeController {

    @GetMapping("/test/probe/boom")
    public String boom() {
        throw new IllegalStateException("segredo interno que não pode vazar");
    }
}
