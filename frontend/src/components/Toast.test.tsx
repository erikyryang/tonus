import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Toast, useToast } from './Toast';

const MESSAGE = 'Aluno desativado.';

/**
 * Two consecutive actions with the same confirmation — deactivating one student and then
 * another — is the case where the repeated notice has to behave like a new notice.
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

/** The toast's timer expires inside React: without act, the disappearance doesn't render. */
function advance(ms: number) {
  act(() => vi.advanceTimersByTime(ms));
}

describe('Toast', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  /**
   * The screen reader announces a mutation inside the live region, not the value of
   * React's state. Repeating the same text without swapping the node changes nothing in the
   * DOM, and the second notice passes silently for whoever depends on it.
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

  /** And the second notice gets its own timer, instead of inheriting the first one's. */
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
