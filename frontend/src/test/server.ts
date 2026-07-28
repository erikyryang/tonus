import { setupServer } from 'msw/node';

/**
 * MSW intercepta na fronteira HTTP. Nenhum teste mocka o apiClient ou o
 * TanStack Query por dentro: os handlers de cada teste são a única simulação.
 */
export const server = setupServer();
