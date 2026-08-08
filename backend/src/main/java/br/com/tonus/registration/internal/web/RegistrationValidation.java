package br.com.tonus.registration.internal.web;

/**
 * The size limits mirror the columns of migrations V004/V005. Without them,
 * an overly long input would only overflow on the INSERT and turn into a 500 instead of a 400.
 */
final class RegistrationValidation {

    /** E.164: '+', non-zero leading digit and 8 to 15 digits in total. */
    static final String PHONE_E164 = "^\\+[1-9]\\d{7,14}$";

    static final String PHONE_MESSAGE = "deve estar no formato E.164, ex.: +5511999990000";

    private RegistrationValidation() {
    }
}
