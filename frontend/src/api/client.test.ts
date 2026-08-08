import { describe, it, expect, vi, afterEach } from 'vitest';
import { apiFetch } from './client';

describe('apiFetch', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('returns parsed JSON on success', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ id: 1, name: 'Retirement' }),
    }));

    const result = await apiFetch<{ id: number; name: string }>('/api/portfolios');
    expect(result).toEqual({ id: 1, name: 'Retirement' });
  });

  it('throws on non-ok response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      text: async () => 'Bad Request',
    }));

    await expect(apiFetch('/api/portfolios')).rejects.toThrow('API error 400');
  });
});
