package br.com.tonus.registration;

/**
 * Contact data of a student. The phone is personal data: whoever consumes it must
 * never log it in full.
 */
public record StudentContact(Long studentId, String name, String phone) {
}
