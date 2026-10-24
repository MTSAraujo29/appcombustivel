import {
  normalizeCnpj,
  normalizeState,
  normalizeStationName,
  normalizeStreet,
  normalizeStreetNumber,
  normalizeText,
} from '../../utils/text.js';
import type { AnpStationEntry } from './anpParser.js';
import type { AnpStationPriceInput } from '../contracts.js';

export interface AnpIndex {
  byCnpj: Map<string, AnpStationEntry>;
  byAddress: Map<string, Set<string>>;
  byName: Map<string, Set<string>>;
}

export interface AnpMatch {
  entry: AnpStationEntry;
  method: 'cnpj' | 'address' | 'name';
}

export function buildAddressKey(
  street: string | undefined,
  number: string | undefined,
  city: string | undefined,
  state: string | undefined,
): string {
  const normalizedStreet = normalizeStreet(street);
  const normalizedNumber = normalizeStreetNumber(number);
  const normalizedCity = normalizeText(city);
  const normalizedState = normalizeState(state);
  if (!normalizedStreet || !normalizedNumber || !normalizedCity || !normalizedState) {
    return '';
  }
  return `${normalizedStreet}|${normalizedNumber}|${normalizedCity}|${normalizedState}`;
}

export function buildNameKey(
  name: string | undefined,
  city: string | undefined,
  state: string | undefined,
): string {
  const normalizedName = normalizeStationName(name);
  const normalizedCity = normalizeText(city);
  const normalizedState = normalizeState(state);
  if (!normalizedName || normalizedName.length < 4 || !normalizedCity || !normalizedState) {
    return '';
  }
  return `${normalizedName}|${normalizedCity}|${normalizedState}`;
}

function addToIndex(index: Map<string, Set<string>>, key: string, cnpj: string): void {
  if (!key) return;
  const current = index.get(key) ?? new Set<string>();
  current.add(cnpj);
  index.set(key, current);
}

export function buildAnpIndex(stations: AnpStationEntry[]): AnpIndex {
  const byCnpj = new Map<string, AnpStationEntry>();
  const byAddress = new Map<string, Set<string>>();
  const byName = new Map<string, Set<string>>();

  for (const station of stations) {
    const cnpj = normalizeCnpj(station.cnpj);
    if (!cnpj) continue;
    byCnpj.set(cnpj, station);

    const addressKey = buildAddressKey(
      station.street,
      station.number,
      station.municipality,
      station.state,
    );
    addToIndex(byAddress, addressKey, cnpj);

    const nameKey = buildNameKey(station.fantasyName, station.municipality, station.state);
    addToIndex(byName, nameKey, cnpj);
  }

  return { byCnpj, byAddress, byName };
}

function uniqueEntry(
  index: Map<string, Set<string>>,
  key: string,
  byCnpj: Map<string, AnpStationEntry>,
): AnpStationEntry | null {
  if (!key) return null;
  const matches = index.get(key);
  if (!matches || matches.size !== 1) return null;
  const [cnpj] = matches;
  return cnpj ? (byCnpj.get(cnpj) ?? null) : null;
}

/**
 * Associação conservadora entre um posto do OpenStreetMap e o levantamento da ANP.
 * Só aceita correspondências sem ambiguidade. O método usado é exposto na API
 * para que o aplicativo possa informar a origem da associação ao usuário.
 */
export function matchAnpStation(index: AnpIndex, input: AnpStationPriceInput): AnpMatch | null {
  const cnpj = normalizeCnpj(input.cnpj);
  if (cnpj) {
    const entry = index.byCnpj.get(cnpj);
    if (entry) return { entry, method: 'cnpj' };
  }

  const addressKey = buildAddressKey(input.street, input.number, input.city, input.state);
  const byAddress = uniqueEntry(index.byAddress, addressKey, index.byCnpj);
  if (byAddress) return { entry: byAddress, method: 'address' };

  // Não usa nomes que são apenas a bandeira (ex.: "Shell", "Ipiranga") porque
  // há dezenas de postos com o mesmo nome em uma cidade.
  if (
    input.name &&
    input.brand &&
    normalizeStationName(input.name) === normalizeStationName(input.brand)
  ) {
    return null;
  }

  const nameKey = buildNameKey(input.name, input.city, input.state);
  const byName = uniqueEntry(index.byName, nameKey, index.byCnpj);
  if (byName) return { entry: byName, method: 'name' };

  return null;
}
