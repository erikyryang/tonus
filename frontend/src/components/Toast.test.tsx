import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Toast, useToast } from './Toast';

const MESSAGE = 'Aluno desativado.';

/**
 * Duas ações seguidas com a mesma confirmação — desativar um aluno e depois
 * outro — é o caso em que o aviso repetido tem de se comportar como aviso novo.
 */
function ToastHarness() {
  const toast = useToast();
  return (
    <>
      <button onClick={() => toast.show(MESSAGE)}>Desativar</button>
      <Toast message={toast.message} serial={toast.serial} />
    </>
  );
}

function showToast() {
  fireEvent.click(screen.getByRole('button', { name: 'Desativar' }));
}

/** O relógio do toast expira dentro do React: sem o act, o sumiço não renderiza. */
function advance(ms: number) {
  act(() => vi.advanceTimersByTime(ms));
}

describe('Toast', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  /**
   * O leitor de tela anuncia mutação dentro da live region, não o valor do
   * estado do React. Repetir o mesmo texto sem trocar o nó não muda nada no
   * DOM, e o segundo aviso passa em silêncio para quem depende dele.
   */
  it('renova o nó do aviso quando a mensagem se repete', () => {
    render(<ToastHarness />);

    showToast();
    const first = screen.getByText(MESSAGE);

    showToast();
    const second = screen.getByText(MESSAGE);

    expect(second).not.toBe(first);
    expect(first).not.toBeInTheDocument();
  });

  /** E o segundo aviso ganha relógio próprio, em vez de herdar o do primeiro. */
  it('reinicia o relógio quando a mensagem se repete', () => {
    vi.useFakeTimers();
    render(<ToastHarness />);

    showToast();
    advance(3800);
    showToast();
    advance(1000);

    expect(screen.getByText(MESSAGE)).toBeInTheDocument();
  });

  it('some sozinho depois do tempo', () => {
    vi.useFakeTimers();
    render(<ToastHarness />);

    showToast();
    advance(4000);

    expect(screen.queryByText(MESSAGE)).not.toBeInTheDocument();
  });
});
