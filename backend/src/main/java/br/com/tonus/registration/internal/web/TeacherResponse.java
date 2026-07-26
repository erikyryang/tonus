package br.com.tonus.registration.internal.web;

import br.com.tonus.registration.internal.domain.Teacher;

public record TeacherResponse(Long id, String name, String phone, boolean active) {

    static TeacherResponse from(Teacher teacher) {
        return new TeacherResponse(teacher.getId(), teacher.getName(), teacher.getPhone(), teacher.isActive());
    }
}
