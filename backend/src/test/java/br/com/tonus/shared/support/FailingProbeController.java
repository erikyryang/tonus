package br.com.tonus.shared.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint that only exists in tests, to trigger an exception that no
 * module maps. It's the only way to exercise shared's last-resort handler —
 * the rest of the API, being well built, never reaches it.
 */
@RestController
public class FailingProbeController {

    @GetMapping("/test/probe/boom")
    public String boom() {
        throw new IllegalStateException("segredo interno que não pode vazar");
    }
}
