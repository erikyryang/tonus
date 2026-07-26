package br.com.tonus;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ArchitectureTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(TonusApplication.class);

    @Test
    void modulesRespectBoundariesAndHaveNoCycles() {
        MODULES.verify();
    }
}
