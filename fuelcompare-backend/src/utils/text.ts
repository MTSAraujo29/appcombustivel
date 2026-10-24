const STATE_BY_NAME: Record<string, string> = {
  acre: 'AC',
  alagoas: 'AL',
  amapa: 'AP',
  amazonas: 'AM',
  bahia: 'BA',
  ceara: 'CE',
  'distrito federal': 'DF',
  'espirito santo': 'ES',
  goias: 'GO',
  maranhao: 'MA',
  'mato grosso': 'MT',
  'mato grosso do sul': 'MS',
  'minas gerais': 'MG',
  para: 'PA',
  paraiba: 'PB',
  parana: 'PR',
  pernambuco: 'PE',
  piaui: 'PI',
  'rio de janeiro': 'RJ',
  'rio grande do norte': 'RN',
  'rio grande do sul': 'RS',
  rondonia: 'RO',
  roraima: 'RR',
  'santa catarina': 'SC',
  'sao paulo': 'SP',
  sergipe: 'SE',
  tocantins: 'TO',
};

const VALID_UFS = new Set(Object.values(STATE_BY_NAME));

/** Remove acentos, caixa e pontuação; usado apenas para comparação. */
export function normalizeText(value: string | undefined | null): string {
  if (!value) return '';
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/** Normaliza o estado brasileiro para a sigla UF (quando reconhecido). */
export function normalizeState(value: string | undefined | null): string {
  const normalized = normalizeText(value);
  if (!normalized) return '';
  const upper = normalized.toUpperCase();
  if (VALID_UFS.has(upper)) return upper;
  return STATE_BY_NAME[normalized] ?? upper;
}

const STREET_PREFIXES: Record<string, string> = {
  r: 'rua',
  'r.': 'rua',
  av: 'avenida',
  'av.': 'avenida',
  al: 'alameda',
  'al.': 'alameda',
  tv: 'travessa',
  trav: 'travessa',
  rod: 'rodovia',
  est: 'estrada',
  pc: 'praca',
  'pça': 'praca',
};

export function normalizeStreet(value: string | undefined | null): string {
  const normalized = normalizeText(value);
  if (!normalized) return '';
  const tokens = normalized.split(' ');
  const first = tokens[0];
  if (first && STREET_PREFIXES[first]) {
    tokens[0] = STREET_PREFIXES[first];
  }
  return tokens.join(' ');
}

export function normalizeStreetNumber(value: string | number | undefined | null): string {
  if (value === undefined || value === null) return '';
  const normalized = String(value)
    .trim()
    .replace(/^(n[ºo°.]?|numero|number)\s*/i, '')
    .replace(/\s+/g, '')
    .toUpperCase();
  if (!normalized || ['SN', 'S/N', 'SEMNUMERO'].includes(normalized.replace(/\s/g, ''))) {
    return '';
  }
  return normalized;
}

/**
 * Nomes de postos na ANP e no OSM variam ("Posto", "Auto Posto", etc.).
 * A chave remove apenas termos genéricos, preservando o nome distintivo.
 */
export function normalizeStationName(value: string | undefined | null): string {
  let normalized = normalizeText(value);
  if (!normalized) return '';
  normalized = normalized
    .replace(/^auto posto\s+/, '')
    .replace(/^posto\s+/, '')
    .replace(/\s+ltda$/, '')
    .replace(/\s+combustiveis$/, '')
    .replace(/\s+derivados de petroleo$/, '')
    .trim();
  return normalized;
}

const LOWERCASE_WORDS = new Set(['da', 'das', 'de', 'do', 'dos', 'e']);

export function toTitleCase(value: string): string {
  return value
    .toLocaleLowerCase('pt-BR')
    .split(/\s+/)
    .map((word, index) =>
      index > 0 && LOWERCASE_WORDS.has(word)
        ? word
        : word.charAt(0).toLocaleUpperCase('pt-BR') + word.slice(1),
    )
    .join(' ');
}

export function digitsOnly(value: string | undefined | null): string {
  return (value ?? '').replace(/\D/g, '');
}

export function normalizeCnpj(value: string | number | undefined | null): string {
  const digits = digitsOnly(value === null || value === undefined ? '' : String(value));
  if (!digits) return '';
  if (digits.length > 14) return digits.slice(-14).padStart(14, '0');
  return digits.padStart(14, '0');
}
