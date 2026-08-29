package br.com.tonus.registration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherCrudIT extends AbstractRegistrationIntegrationTest {

    @Test
    void createsTeacherActiveByDefault() throws Exception {
        String name = unique("Professora Ana");

        mockMvc.perform(post("/teachers")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phone":"+5511999991234"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.phone").value("+5511999991234"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getsTeacherById() throws Exception {
        String name = unique("Professor Bruno");
        long id = createTeacher(name);

        mockMvc.perform(get("/teachers/{id}", id).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value(name));
    }

    @Test
    void updatesTeacher() throws Exception {
        long id = createTeacher(unique("Professora Carla"));
        String newName = unique("Professora Carla Souza");

        mockMvc.perform(put("/teachers/{id}", id)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phone":"+5511977776666"}
                                """.formatted(newName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newName))
                .andExpect(jsonPath("$.phone").value("+5511977776666"));

        mockMvc.perform(get("/teachers/{id}", id).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newName));
    }

    @Test
    void rejectsTeacherWithoutName() throws Exception {
        mockMvc.perform(post("/teachers")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"+5511999991234"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void returnsNotFoundForUnknownTeacher() throws Exception {
        mockMvc.perform(get("/teachers/{id}", 99_999_999L).cookie(admin))
                .andExpect(status().isNotFound());
    }
}
