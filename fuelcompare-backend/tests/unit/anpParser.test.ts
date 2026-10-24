import ExcelJS from 'exceljs';
import { describe, expect, it } from 'vitest';
import {
  mapAnpProduct,
  parseResumoWorkbook,
  parseRevendasWorkbook,
  toYmd,
} from '../../src/providers/anp/anpParser.js';

async function workbookBuffer(
  rows: (string | number | Date | null)[][],
  sheetName: string,
): Promise<Buffer> {
  const workbook = new ExcelJS.Workbook();
  const worksheet = workbook.addWorksheet(sheetName);
  for (const row of rows) worksheet.addRow(row);
  const output = await workbook.xlsx.writeBuffer();
  return Buffer.from(output as ArrayBuffer);
}

const REVENDAS_HEADER = [
  'CNPJ',
  'RAZÃO',
  'FANTASIA',
  'ENDEREÇO',
  'NÚMERO',
  'COMPLEMENTO',
  'BAIRRO',
  'CEP',
  'MUNICÍPIO',
  'ESTADO',
  'BANDEIRA',
  'PRODUTO',
  'UNIDADE DE MEDIDA',
  'PREÇO DE REVENDA',
  'DATA DA COLETA',
];

function revendasRow(
  overrides: {
    cnpj?: string | number | null;
    produto?: string;
    preco?: number | string;
    data?: string | Date;
  } = {},
): (string | number | Date | null)[] {
  return [
    overrides.cnpj === undefined ? '11.222.333/0001-81' : overrides.cnpj,
    'AUTO POSTO CENTRAL LTDA',
    'AUTO POSTO CENTRAL',
    'AVENIDA PAULISTA',
    1000,
    null,
    'BELA VISTA',
    '01310-100',
    'SAO PAULO',
    'SÃO PAULO',
    'SHELL',
    overrides.produto ?? 'GASOLINA COMUM',
    'R$ / Litro',
    overrides.preco === undefined ? 5.89 : overrides.preco,
    overrides.data === undefined ? '11/10/2026' : overrides.data,
  ];
}

describe('mapAnpProduct', () => {
  it('mapeia os produtos do levantamento para os códigos internos', () => {
    expect(mapAnpProduct('GASOLINA COMUM')).toBe('gasoline');
    expect(mapAnpProduct('GASOLINA ADITIVADA')).toBe('gasoline_additivated');
    expect(mapAnpProduct('ETANOL')).toBe('ethanol');
    expect(mapAnpProduct('ÓLEO DIESEL S10')).toBe('diesel_s10');
    expect(mapAnpProduct('DIESEL')).toBe('diesel');
    expect(mapAnpProduct('GNV')).toBe('gnv');
    expect(mapAnpProduct('GLP')).toBeNull();
    expect(mapAnpProduct('')).toBeNull();
  });
});

describe('toYmd', () => {
  it('converte datas brasileiras, ISO e Date', () => {
    expect(toYmd('11/10/2026')).toBe('2026-10-11');
    expect(toYmd('2026-10-11')).toBe('2026-10-11');
    expect(toYmd('2026-10-11T12:00:00.000Z')).toBe('2026-10-11');
    expect(toYmd(new Date(Date.UTC(2026, 9, 11)))).toBe('2026-10-11');
    expect(toYmd('data inválida')).toBeUndefined();
    expect(toYmd(undefined)).toBeUndefined();
  });
});

describe('parseRevendasWorkbook', () => {
  it('extrai preços individuais, mantém a coleta mais recente e ignora produtos fora do escopo', async () => {
    const buffer = await workbookBuffer(
      [
        ['AGÊNCIA NACIONAL DO PETRÓLEO, GÁS NATURAL E BIOCOMBUSTÍVEIS - ANP'],
        ['PERÍODO: 11/10/2026 A 17/10/2026'],
        [],
        REVENDAS_HEADER,
        revendasRow({ produto: 'GASOLINA COMUM', preco: 5.89, data: '11/10/2026' }),
        revendasRow({ produto: 'ETANOL', preco: '3,49', data: '12/10/2026' }),
        revendasRow({ produto: 'DIESEL S10', preco: 6.09, data: new Date(Date.UTC(2026, 9, 13)) }),
        revendasRow({ produto: 'DIESEL', preco: 6.29, data: '13/10/2026' }),
        revendasRow({ produto: 'GNV', preco: 4.59, data: '14/10/2026' }),
        revendasRow({ produto: 'GLP', preco: 110, data: '14/10/2026' }),
        revendasRow({ produto: 'GASOLINA COMUM', preco: 0, data: '14/10/2026' }),
        revendasRow({ produto: 'GASOLINA COMUM', preco: 5.99, data: '10/10/2026' }),
        revendasRow({ cnpj: null, produto: 'GASOLINA COMUM', preco: 5.79 }),
      ],
      'POSTOS REVENDEDORES',
    );

    const parsed = await parseRevendasWorkbook(buffer, 'https://exemplo.test/revendas.xlsx');

    expect(parsed.stations).toHaveLength(1);
    expect(parsed.period).toEqual({ start: '2026-10-11', end: '2026-10-17' });
    const station = parsed.stations[0];
    expect(station).toMatchObject({
      cnpj: '11222333000181',
      fantasyName: 'AUTO POSTO CENTRAL',
      street: 'AVENIDA PAULISTA',
      number: '1000',
      municipality: 'Sao Paulo',
      state: 'SP',
      brand: 'SHELL',
    });
    expect([...(station?.products.keys() ?? [])]).toEqual([
      'gasoline',
      'ethanol',
      'diesel_s10',
      'diesel',
      'gnv',
    ]);
    expect(station?.products.get('gasoline')).toMatchObject({
      amount: 5.89,
      collectedAt: '2026-10-11',
    });
    expect(station?.products.get('ethanol')?.amount).toBe(3.49);
    expect(station?.products.get('diesel_s10')?.collectedAt).toBe('2026-10-13');
    expect(parsed.warnings.some((warning) => warning.includes('ignoradas'))).toBe(true);
  });

  it('associa produtos ao mesmo posto por CNPJ, mesmo em linhas separadas', async () => {
    const buffer = await workbookBuffer(
      [
        ['PERÍODO: 11/10/2026 A 17/10/2026'],
        REVENDAS_HEADER,
        revendasRow({ produto: 'GASOLINA COMUM', preco: 5.89 }),
        revendasRow({ produto: 'ETANOL', preco: 3.49 }),
      ],
      'POSTOS REVENDEDORES',
    );
    const parsed = await parseRevendasWorkbook(buffer, 'local');
    expect(parsed.stations).toHaveLength(1);
    expect(parsed.stations[0]?.products.size).toBe(2);
  });

  it('informa quando o cabeçalho esperado não está presente', async () => {
    const buffer = await workbookBuffer([['planilha inesperada'], [123, 'sem cabeçalho']], 'Planilha');
    const parsed = await parseRevendasWorkbook(buffer, 'local');
    expect(parsed.stations).toEqual([]);
    expect(parsed.warnings[0]).toContain('cabeçalho');
  });
});

describe('parseResumoWorkbook', () => {
  const RESUMO_HEADER = [
    'MUNICÍPIO',
    'ESTADO',
    'PRODUTO',
    'PREÇO MÉDIO REVENDA',
    'PREÇO MÍNIMO REVENDA',
    'PREÇO MÁXIMO REVENDA',
    'DATA INICIAL',
    'DATA FINAL',
    'NÚMERO DE POSTOS PESQUISADOS',
  ];

  function resumoRow(
    fuel: string,
    average: number,
    overrides: { municipality?: string; start?: string; end?: string; surveyed?: number } = {},
  ): (string | number)[] {
    return [
      overrides.municipality ?? 'SAO PAULO',
      'SÃO PAULO',
      fuel,
      average,
      average - 0.4,
      average + 0.6,
      overrides.start ?? '11/10/2026',
      overrides.end ?? '17/10/2026',
      overrides.surveyed ?? 120,
    ];
  }

  it('extrai médias municipais e mantém o tipo de preço separado', async () => {
    const buffer = await workbookBuffer(
      [
        ['RESUMO SEMANAL'],
        RESUMO_HEADER,
        resumoRow('GASOLINA COMUM', 6.1),
        resumoRow('ETANOL', 4.2),
        resumoRow('GASOLINA COMUM', 9.99),
      ],
      'MUNICIPIOS',
    );
    const parsed = await parseResumoWorkbook(buffer, 'https://exemplo.test/resumo.xlsx');
    expect(parsed.period).toEqual({ start: '2026-10-11', end: '2026-10-17' });
    expect(parsed.prices).toHaveLength(2);
    expect(parsed.prices[0]).toMatchObject({
      fuel: 'gasoline',
      amount: 6.1,
      minAmount: 5.7,
      maxAmount: 6.7,
      priceType: 'municipal_average',
      municipality: 'Sao Paulo',
      state: 'SP',
      surveyedStations: 120,
      referenceStart: '2026-10-11',
      referenceEnd: '2026-10-17',
      source: { official: true },
    });
  });

  it('ignora linhas sem datas de referência', async () => {
    const buffer = await workbookBuffer(
      [RESUMO_HEADER, resumoRow('GNV', 4.59, { start: '', end: '' })],
      'MUNICIPIOS',
    );
    const parsed = await parseResumoWorkbook(buffer, 'local');
    expect(parsed.prices).toEqual([]);
    expect(parsed.warnings[0]).toContain('média municipal');
  });

  it('informa quando a aba de municípios não existe ou está vazia', async () => {
    const buffer = await workbookBuffer([['nada']], 'OUTRA');
    const parsed = await parseResumoWorkbook(buffer, 'local');
    expect(parsed.prices).toEqual([]);
    expect(parsed.warnings[0]).toContain('aba');
  });
});
