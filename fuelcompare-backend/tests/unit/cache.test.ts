import { describe, expect, it, vi } from 'vitest';
import { TtlCache } from '../../src/utils/cache.js';

function fakeClock(start = 1_000) {
  let now = start;
  return {
    now: () => now,
    advance: (ms: number) => {
      now += ms;
    },
  };
}

describe('TtlCache', () => {
  it('armazena, recupera e contabiliza acertos e erros', () => {
    const cache = new TtlCache<string>(1_000);
    expect(cache.get('a')).toBeUndefined();
    cache.set('a', 'valor');
    expect(cache.get('a')).toBe('valor');
    expect(cache.stats()).toMatchObject({ hits: 1, misses: 1, size: 1 });
  });

  it('expira valores após o TTL, mas mantém o valor para fallback', () => {
    const clock = fakeClock();
    const cache = new TtlCache<string>(500, clock.now);
    cache.set('a', 'antigo');
    clock.advance(499);
    expect(cache.get('a')).toBe('antigo');
    clock.advance(2);
    expect(cache.get('a')).toBeUndefined();
    expect(cache.peek('a')).toBe('antigo');
    expect(cache.stats().size).toBe(0);
  });

  it('coalesce cargas concorrentes da mesma chave', async () => {
    const cache = new TtlCache<number>(1_000);
    let resolveLoader: ((value: number) => void) | undefined;
    const loader = vi.fn(
      () =>
        new Promise<number>((resolve) => {
          resolveLoader = resolve;
        }),
    );

    const first = cache.getOrLoad('k', loader);
    const second = cache.getOrLoad('k', loader);
    resolveLoader?.(42);

    await expect(first).resolves.toEqual({ value: 42, cached: false });
    await expect(second).resolves.toEqual({ value: 42, cached: false });
    expect(loader).toHaveBeenCalledTimes(1);

    const third = cache.getOrLoad('k', loader);
    await expect(third).resolves.toEqual({ value: 42, cached: true });
    expect(loader).toHaveBeenCalledTimes(1);
  });

  it('não armazena falhas e libera a chave em voo para nova tentativa', async () => {
    const cache = new TtlCache<number>(1_000);
    const failing = vi.fn(async () => {
      throw new Error('falha externa');
    });

    await expect(cache.getOrLoad('k', failing)).rejects.toThrow('falha externa');
    const succeeding = vi.fn(async () => 7);
    await expect(cache.getOrLoad('k', succeeding)).resolves.toEqual({
      value: 7,
      cached: false,
    });
    expect(failing).toHaveBeenCalledTimes(1);
    expect(succeeding).toHaveBeenCalledTimes(1);
  });

  it('respeita o limite de entradas removendo a mais antiga', () => {
    const cache = new TtlCache<number>(1_000, Date.now, 2);
    cache.set('a', 1);
    cache.set('b', 2);
    cache.set('c', 3);
    expect(cache.peek('a')).toBeUndefined();
    expect(cache.stats().size).toBe(2);
  });

  it('permite remover e limpar entradas', () => {
    const cache = new TtlCache<number>(1_000);
    cache.set('a', 1);
    cache.set('b', 2);
    cache.delete('a');
    expect(cache.peek('a')).toBeUndefined();
    cache.clear();
    expect(cache.stats().size).toBe(0);
  });
});
