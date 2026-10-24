export interface CacheStats {
  hits: number;
  misses: number;
  size: number;
}

interface Entry<T> {
  value: T;
  expiresAt: number;
}

/**
 * Cache em memória com TTL e coalescência de cargas concorrentes.
 * A interface é compatível com uma futura implementação Redis.
 */
export class TtlCache<T> {
  private readonly entries = new Map<string, Entry<T>>();
  private readonly inflight = new Map<string, Promise<T>>();
  private hits = 0;
  private misses = 0;

  constructor(
    private readonly ttlMs: number,
    private readonly clock: () => number = Date.now,
    private readonly maxEntries = 1_000,
  ) {}

  get(key: string): T | undefined {
    const entry = this.entries.get(key);
    if (!entry) {
      this.misses += 1;
      return undefined;
    }
    if (entry.expiresAt <= this.clock()) {
      this.misses += 1;
      return undefined;
    }
    this.hits += 1;
    return entry.value;
  }

  /** Retorna um valor mesmo expirado; usado como fallback quando o provedor falha. */
  peek(key: string): T | undefined {
    return this.entries.get(key)?.value;
  }

  set(key: string, value: T): void {
    if (this.entries.size >= this.maxEntries) {
      const oldestKey = this.entries.keys().next().value;
      if (oldestKey !== undefined) this.entries.delete(oldestKey);
    }
    this.entries.set(key, { value, expiresAt: this.clock() + this.ttlMs });
  }

  async getOrLoad(key: string, loader: () => Promise<T>): Promise<{ value: T; cached: boolean }> {
    const cached = this.get(key);
    if (cached !== undefined) {
      return { value: cached, cached: true };
    }

    const pending = this.inflight.get(key);
    if (pending) {
      return { value: await pending, cached: false };
    }

    const loadPromise = loader()
      .then((value) => {
        this.set(key, value);
        return value;
      })
      .finally(() => {
        this.inflight.delete(key);
      });

    this.inflight.set(key, loadPromise);
    return { value: await loadPromise, cached: false };
  }

  delete(key: string): void {
    this.entries.delete(key);
  }

  clear(): void {
    this.entries.clear();
    this.inflight.clear();
  }

  stats(): CacheStats {
    const now = this.clock();
    let active = 0;
    for (const entry of this.entries.values()) {
      if (entry.expiresAt > now) active += 1;
    }
    return { hits: this.hits, misses: this.misses, size: active };
  }
}
