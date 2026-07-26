package br.com.tonus.registration;

public record StudentSummary(Long studentId, String name, Long teacherId, boolean active) {
}
