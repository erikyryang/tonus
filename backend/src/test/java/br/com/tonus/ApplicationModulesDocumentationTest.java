package br.com.tonus;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ApplicationModulesDocumentationTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(TonusApplication.class);

    @Test
    void writesModuleDiagramsAndCanvases() {
        new Documenter(MODULES)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeDocumentation();
    }
}
