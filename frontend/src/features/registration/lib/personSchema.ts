import { z } from 'zod';

/**
 * Espelha o @Pattern do back: '+', dígito inicial não-zero, 8 a 15 dígitos.
 * A duplicação é a permitida pelo CLAUDE.md (validação de formulário) — o back
 * continua sendo a autoridade, mas descobrir o formato só depois do 400
 * obrigaria a redigitar o cadastro inteiro.
 */
export const PHONE_E164 = /^\+[1-9]\d{7,14}$/;

export const PHONE_MESSAGE = 'O telefone deve estar no formato E.164, ex.: +5511999990000';

/** Os limites do back: nome obrigatório e de no máximo 255 (a coluna). */
export const personFields = {
  name: z
    .string()
    .trim()
    .min(1, 'Informe o nome.')
    .max(255, 'O nome pode ter no máximo 255 caracteres.'),
  phone: z.string().trim().regex(PHONE_E164, PHONE_MESSAGE),
};
