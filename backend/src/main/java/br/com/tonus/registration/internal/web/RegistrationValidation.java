package br.com.tonus.registration.internal.web;

/**
 * Os limites de tamanho espelham as colunas das migrations V004/V005. Sem eles,
 * uma entrada longa demais só estouraria no INSERT e viraria 500 em vez de 400.
 */
final class RegistrationValidation {

    /** E.164: '+', dígito inicial não-zero e 8 a 15 dígitos no total. */
    static final String PHONE_E164 = "^\\+[1-9]\\d{7,14}$";

    static final String PHONE_MESSAGE = "deve estar no formato E.164, ex.: +5511999990000";

    private RegistrationValidation() {
    }
}
