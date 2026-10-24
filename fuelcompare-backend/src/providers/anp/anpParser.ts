import ExcelJS from 'exceljs';
import {
  FUEL_INFO,
  type FuelCode,
  type RegionalPrice,
  type SourceRef,
} from '../../types.js';
import {
  normalizeCnpj,
  normalizeState,
  normalizeText,
  toTitleCase,
} from '../../utils/text.js';

export interface AnpProductPrice {
  amount: number;
  collectedAt?: string;
}

export interface AnpStationEntry {
  cnpj: string;
  fantasyName: string;
  corporateName?: string;
  street?: string;
  number?: string;
  neighborhood?: string;
  postalCode?: string;
  municipality: string;
  state: string;
  brand?: string;
  products: Map<FuelCode, AnpProductPrice>;
}

export interface AnpPeriod {
  start: string;
  end: string;
}

export interface ParsedStationPrices {
  stations: AnpStationEntry[];
  period?: AnpPeriod;
  warnings: string[];
}

export interface ParsedRegionalPrices {
  prices: RegionalPrice[];
  period?: AnpPeriod;
  warnings: string[];
}

type HeaderMap = Map<string, number>;

const CNPJ_SOURCE_NAME = 'ANP - Levantamento de Preços de Combustíveis';

function cellText(value: ExcelJS.CellValue): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'string') return value.trim();
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (value instanceof Date) return toYmd(value) ?? '';
  if (typeof value === 'object') {
    if ('richText' in value && Array.isArray(value.richText)) {
      return value.richText.map((part) => part.text).join('').trim();
    }
    if ('text' in value && typeof value.text === 'string') return value.text.trim();
    if ('result' in value) return cellText(value.result as ExcelJS.CellValue);
    if ('error' in value) return '';
  }
  return '';
}

function cellNumber(value: ExcelJS.CellValue): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string') {
    const parsed = Number(value.replace(',', '.').replace(/[^\d.-]/g, ''));
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  if (value && typeof value === 'object' && 'result' in value) {
    return cellNumber(value.result as ExcelJS.CellValue);
  }
  return undefined;
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

export function toYmd(value: Date | string | undefined | null): string | undefined {
  if (!value) return undefined;
  if (typeof value === 'string') {
    const brazilian = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value.trim());
    if (brazilian) {
      const [, day, month, year] = brazilian;
      return `${year}-${month}-${day}`;
    }
    const iso = /^(\d{4})-(\d{2})-(\d{2})/.exec(value.trim());
    return iso ? `${iso[1]}-${iso[2]}-${iso[3]}` : undefined;
  }
  if (Number.isNaN(value.getTime())) return undefined;
  const year = value.getUTCFullYear();
  return `${year}-${pad(value.getUTCMonth() + 1)}-${pad(value.getUTCDate())}`;
}

function cellDate(value: ExcelJS.CellValue): string | undefined {
  if (value instanceof Date) return toYmd(value);
  if (typeof value === 'string') return toYmd(value);
  if (value && typeof value === 'object' && 'result' in value) {
    return cellDate(value.result as ExcelJS.CellValue);
  }
  return undefined;
}

export function mapAnpProduct(rawProduct: string): FuelCode | null {
  const normalized = normalizeText(rawProduct).toUpperCase();
  if (!normalized) return null;
  if (normalized.includes('GNV') || normalized.includes('GAS NATURAL VEICULAR')) {
    return 'gnv';
  }
  if (normalized.includes('DIESEL')) {
    return normalized.includes('S10') ? 'diesel_s10' : 'diesel';
  }
  if (normalized.includes('ETANOL')) {
    return 'ethanol';
  }
  if (normalized.includes('GASOLINA')) {
    return normalized.includes('ADITIVADA') ? 'gasoline_additivated' : 'gasoline';
  }
  return null;
}

function isValidPrice(value: number | undefined): value is number {
  return value !== undefined && Number.isFinite(value) && value > 0 && value <= 100;
}

function roundPrice(value: number): number {
  return Math.round(value * 1000) / 1000;
}

function buildHeader(row: ExcelJS.Row): HeaderMap {
  const header = new Map<string, number>();
  row.eachCell({ includeEmpty: false }, (cell, colNumber) => {
    const normalized = normalizeText(cellText(cell.value));
    if (normalized) header.set(normalized, colNumber);
  });
  return header;
}

function findColumn(header: HeaderMap, ...candidates: string[]): number | undefined {
  for (const candidate of candidates) {
    const normalized = normalizeText(candidate);
    const exact = header.get(normalized);
    if (exact) return exact;
  }
  for (const [key, value] of header) {
    if (candidates.some((candidate) => key.includes(normalizeText(candidate)))) {
      return value;
    }
  }
  return undefined;
}

function getText(row: ExcelJS.Row, header: HeaderMap, ...names: string[]): string {
  const column = findColumn(header, ...names);
  return column ? cellText(row.getCell(column).value) : '';
}

function getNumber(row: ExcelJS.Row, header: HeaderMap, ...names: string[]): number | undefined {
  const column = findColumn(header, ...names);
  return column ? cellNumber(row.getCell(column).value) : undefined;
}

function getDate(row: ExcelJS.Row, header: HeaderMap, ...names: string[]): string | undefined {
  const column = findColumn(header, ...names);
  return column ? cellDate(row.getCell(column).value) : undefined;
}

function detectPeriod(worksheet: ExcelJS.Worksheet): AnpPeriod | undefined {
  let found: AnpPeriod | undefined;
  for (let rowNumber = 1; rowNumber <= Math.min(worksheet.rowCount, 10); rowNumber += 1) {
    const row = worksheet.getRow(rowNumber);
    row.eachCell({ includeEmpty: false }, (cell) => {
      if (found) return;
      const text = cellText(cell.value);
      const match = /(\d{2}\/\d{2}\/\d{4})\s*(?:A|ATÉ|-)\s*(\d{2}\/\d{2}\/\d{4})/i.exec(
        text,
      );
      if (match?.[1] && match[2]) {
        const start = toYmd(match[1]);
        const end = toYmd(match[2]);
        if (start && end) found = { start, end };
      }
    });
    if (found) break;
  }
  return found;
}

function findHeaderRow(worksheet: ExcelJS.Worksheet, required: string[]): number | undefined {
  for (let rowNumber = 1; rowNumber <= Math.min(worksheet.rowCount, 30); rowNumber += 1) {
    const header = buildHeader(worksheet.getRow(rowNumber));
    if (required.every((name) => findColumn(header, name) !== undefined)) {
      return rowNumber;
    }
  }
  return undefined;
}

export async function parseRevendasWorkbook(
  buffer: Buffer,
  _sourceUrl: string,
): Promise<ParsedStationPrices> {
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(buffer as unknown as ArrayBuffer);
  const worksheet =
    workbook.worksheets.find((sheet) =>
      findHeaderRow(sheet, ['CNPJ', 'MUNICIPIO', 'PRODUTO']) !== undefined,
    ) ?? workbook.worksheets[0];

  if (!worksheet) {
    return { stations: [], warnings: ['A planilha de revendas da ANP não possui abas.'] };
  }

  const headerRowNumber = findHeaderRow(worksheet, ['CNPJ', 'MUNICIPIO', 'PRODUTO']);
  if (!headerRowNumber) {
    return {
      stations: [],
      warnings: ['A planilha de revendas da ANP não possui o cabeçalho esperado.'],
    };
  }

  const header = buildHeader(worksheet.getRow(headerRowNumber));
  const byCnpj = new Map<string, AnpStationEntry>();
  const warnings: string[] = [];
  let skippedRows = 0;

  worksheet.eachRow({ includeEmpty: false }, (row, rowNumber) => {
    if (rowNumber <= headerRowNumber) return;
    const cnpj = normalizeCnpj(getText(row, header, 'CNPJ'));
    if (!cnpj) {
      skippedRows += 1;
      return;
    }
    const productRaw = getText(row, header, 'PRODUTO');
    const fuel = mapAnpProduct(productRaw);
    const amount = getNumber(row, header, 'PRECO DE REVENDA', 'PRECO');
    if (!fuel || !isValidPrice(amount)) {
      skippedRows += 1;
      return;
    }

    const municipalityRaw = getText(row, header, 'MUNICIPIO');
    const stateRaw = getText(row, header, 'ESTADO');
    const municipality = municipalityRaw ? toTitleCase(municipalityRaw) : '';
    const state = normalizeState(stateRaw);
    if (!municipality || !state) {
      skippedRows += 1;
      return;
    }

    const collectedAt = getDate(row, header, 'DATA DA COLETA');
    const existing = byCnpj.get(cnpj) ?? {
      cnpj,
      fantasyName: getText(row, header, 'FANTASIA') || getText(row, header, 'RAZAO'),
      corporateName: getText(row, header, 'RAZAO') || undefined,
      street: getText(row, header, 'ENDERECO') || undefined,
      number: getText(row, header, 'NUMERO') || undefined,
      neighborhood: getText(row, header, 'BAIRRO') || undefined,
      postalCode: getText(row, header, 'CEP') || undefined,
      municipality,
      state,
      brand: getText(row, header, 'BANDEIRA') || undefined,
      products: new Map<FuelCode, AnpProductPrice>(),
    };

    const previous = existing.products.get(fuel);
    if (!previous || (collectedAt ?? '') >= (previous.collectedAt ?? '')) {
      existing.products.set(fuel, {
        amount: roundPrice(amount),
        ...(collectedAt ? { collectedAt } : {}),
      });
    }
    byCnpj.set(cnpj, existing);
  });

  if (skippedRows > 0) {
    warnings.push(
      `${skippedRows} linha(s) da planilha foram ignoradas por não terem preço válido, CNPJ ou município.`,
    );
  }
  if (byCnpj.size === 0) {
    warnings.push('Nenhum preço individual pôde ser extraído da planilha da ANP.');
  }

  return {
    stations: [...byCnpj.values()],
    ...(detectPeriod(worksheet) ? { period: detectPeriod(worksheet) } : {}),
    warnings,
  };
}

export async function parseResumoWorkbook(
  buffer: Buffer,
  sourceUrl: string,
): Promise<ParsedRegionalPrices> {
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(buffer as unknown as ArrayBuffer);
  const worksheet =
    workbook.worksheets.find((sheet) => normalizeText(sheet.name) === 'municipios') ??
    workbook.worksheets.find(
      (sheet) => findHeaderRow(sheet, ['MUNICIPIO', 'PRODUTO', 'PRECO MEDIO']) !== undefined,
    );

  if (!worksheet) {
    return { prices: [], warnings: ['A planilha de resumo da ANP não possui a aba de municípios.'] };
  }

  const headerRowNumber = findHeaderRow(worksheet, ['MUNICIPIO', 'PRODUTO', 'PRECO MEDIO']);
  if (!headerRowNumber) {
    return {
      prices: [],
      warnings: ['A planilha de resumo da ANP não possui o cabeçalho esperado.'],
    };
  }

  const header = buildHeader(worksheet.getRow(headerRowNumber));
  const source: SourceRef = {
    name: CNPJ_SOURCE_NAME,
    url: sourceUrl,
    official: true,
  };
  const prices: RegionalPrice[] = [];
  const seen = new Set<string>();
  let detectedPeriod: AnpPeriod | undefined;

  worksheet.eachRow({ includeEmpty: false }, (row, rowNumber) => {
    if (rowNumber <= headerRowNumber) return;
    const productRaw = getText(row, header, 'PRODUTO');
    const fuel = mapAnpProduct(productRaw);
    const municipalityRaw = getText(row, header, 'MUNICIPIO');
    const state = normalizeState(getText(row, header, 'ESTADO'));
    const amount = getNumber(row, header, 'PRECO MEDIO REVENDA', 'PRECO MEDIO');
    if (!fuel || !municipalityRaw || !state || !isValidPrice(amount)) {
      return;
    }

    const municipality = toTitleCase(municipalityRaw);
    const key = `${normalizeText(municipality)}|${state}|${fuel}`;

    const start = getDate(row, header, 'DATA INICIAL');
    const end = getDate(row, header, 'DATA FINAL');
    if (!start || !end) return;
    if (seen.has(key)) return;
    seen.add(key);

    const min = getNumber(row, header, 'PRECO MINIMO REVENDA', 'PRECO MINIMO');
    const max = getNumber(row, header, 'PRECO MAXIMO REVENDA', 'PRECO MAXIMO');
    const surveyed = getNumber(row, header, 'NUMERO DE POSTOS PESQUISADOS', 'POSTOS PESQUISADOS');
    if (!detectedPeriod) detectedPeriod = { start, end };

    prices.push({
      fuel,
      amount: roundPrice(amount),
      minAmount: min !== undefined && isValidPrice(min) ? roundPrice(min) : roundPrice(amount),
      maxAmount: max !== undefined && isValidPrice(max) ? roundPrice(max) : roundPrice(amount),
      currency: 'BRL',
      unit: FUEL_INFO[fuel].unit,
      priceType: 'municipal_average',
      municipality,
      state,
      referenceStart: start,
      referenceEnd: end,
      surveyedStations: surveyed && surveyed > 0 ? Math.round(surveyed) : 0,
      source,
    });
  });

  // Linhas de produtos fora do escopo (GLP, diesel de distribuição etc.) são descartadas
  // silenciosamente para não gerar ruído operacional no resumo.
  return {
    prices,
    ...(detectedPeriod ? { period: detectedPeriod } : {}),
    warnings:
      prices.length === 0
        ? ['Nenhuma média municipal pôde ser extraída da planilha da ANP.']
        : [],
  };
}

export const ANP_SOURCE_NAME = CNPJ_SOURCE_NAME;
