package br.com.tonus.registration.internal.web;

import br.com.tonus.registration.internal.domain.Student;

public record StudentResponse(Long id, String name, String phone, Long teacherId, boolean active) {

    static StudentResponse from(Student student) {
        return new StudentResponse(student.getId(), student.getName(), student.getPhone(),
                student.getTeacherId(), student.isActive());
    }
}
