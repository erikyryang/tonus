package br.com.tonus.registration;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationListingFilterIT extends AbstractRegistrationIntegrationTest {

    @Test
    void filtersStudentsByActiveFlag() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Filtro"));
        long active = createStudent(unique("Aluno Filtro Ativo"), teacherId);
        long inactive = createStudent(unique("Aluno Filtro Inativo"), teacherId);

        mockMvc.perform(patch("/students/{id}/deactivate", inactive).cookie(admin))
                .andExpect(status().isOk());

        assertThat(allIds("/students", "active", "true")).contains(active).doesNotContain(inactive);
        assertThat(allIds("/students", "active", "false")).contains(inactive).doesNotContain(active);
    }

    @Test
    void listsEverythingWithoutTheFilter() throws Exception {
        long teacherId = createTeacher(unique("Professor Sem Filtro"));
        long active = createStudent(unique("Aluno Sem Filtro Ativo"), teacherId);
        long inactive = createStudent(unique("Aluno Sem Filtro Inativo"), teacherId);

        mockMvc.perform(patch("/students/{id}/deactivate", inactive).cookie(admin))
                .andExpect(status().isOk());

        assertThat(allIds("/students")).contains(active, inactive);
    }

    @Test
    void filtersTeachersByActiveFlag() throws Exception {
        long active = createTeacher(unique("Professor Filtro Ativo"));
        long inactive = createTeacher(unique("Professor Filtro Inativo"));

        mockMvc.perform(patch("/teachers/{id}/deactivate", inactive).cookie(admin))
                .andExpect(status().isOk());

        assertThat(allIds("/teachers", "active", "true")).contains(active).doesNotContain(inactive);
        assertThat(allIds("/teachers", "active", "false")).contains(inactive).doesNotContain(active);
    }

    /**
     * Paginação sem ORDER BY total é não-determinística no Postgres. Varrendo
     * as páginas de duas em duas, nenhum id pode se repetir nem sumir.
     */
    @Test
    void pagesDoNotOverlapOrLoseRows() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Ordem"));
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            created.add(createStudent(unique("Aluno Ordem"), teacherId));
        }

        List<Long> seen = new ArrayList<>();
        int page = 0;
        while (true) {
            List<Long> ids = idsOf(pageOf("/students", page, 2));
            if (ids.isEmpty()) {
                break;
            }
            seen.addAll(ids);
            page++;
        }

        assertThat(seen).doesNotHaveDuplicates().containsAll(created);
    }

    /**
     * Os nomes são minúsculos, sem espaço e sem acento de propósito: o Postgres
     * ordena com a collation do banco (en_US ignora caixa e pontuação no nível
     * primário), que não coincide com String::compareTo. Restringir o alfabeto
     * torna a ordem esperada a mesma nas duas.
     */
    @Test
    void sortsByNameByDefault() throws Exception {
        String prefix = "zzsort" + unique("").replace("-", "").toLowerCase();
        createTeacher(prefix + "c");
        createTeacher(prefix + "a");
        createTeacher(prefix + "b");

        List<String> ordered = allNames("/teachers").stream()
                .filter(name -> name.startsWith(prefix))
                .toList();

        assertThat(ordered).containsExactly(prefix + "a", prefix + "b", prefix + "c");
    }

    @Test
    void rejectsNothingWhenFilterAbsent() throws Exception {
        mockMvc.perform(get("/students").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    private MvcResult pageOf(String path, int page, int size) throws Exception {
        return mockMvc.perform(get(path).cookie(admin)
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private List<Long> idsOf(MvcResult result) throws Exception {
        List<Long> ids = new ArrayList<>();
        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        content.forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    private List<String> allNames(String path) throws Exception {
        List<String> names = new ArrayList<>();
        int page = 0;
        while (true) {
            JsonNode content = objectMapper
                    .readTree(pageOf(path, page, 100).getResponse().getContentAsString())
                    .get("content");
            if (content.isEmpty()) {
                return names;
            }
            content.forEach(node -> names.add(node.get("name").asText()));
            page++;
        }
    }

    private List<Long> allIds(String path, String... params) throws Exception {
        List<Long> ids = new ArrayList<>();
        int page = 0;
        while (true) {
            var request = get(path).cookie(admin)
                    .param("page", String.valueOf(page))
                    .param("size", "100");
            for (int i = 0; i < params.length; i += 2) {
                request = request.param(params[i], params[i + 1]);
            }
            List<Long> pageIds = idsOf(mockMvc.perform(request).andExpect(status().isOk()).andReturn());
            if (pageIds.isEmpty()) {
                return ids;
            }
            ids.addAll(pageIds);
            page++;
        }
    }
}
