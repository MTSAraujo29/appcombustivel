import { describe, expect, it } from 'vitest';
import {
  buildComparison,
  collectRegionalReference,
} from '../../src/services/comparisonService.js';
import { makePrice, makeRegionalPrice } from '../helpers/fixtures.js';

describe('buildComparison', () => {
  it('compara preços do mesmo combustível e indica o mais barato', () => {
    const comparison = buildComparison(
      {
        id: 'node_1',
        name: 'Posto A',
        prices: [makePrice('gasoline', 5.89), makePrice('ethanol', 4.1)],
      },
      {
        id: 'node_2',
        name: 'Posto B',
        prices: [makePrice('gasoline', 6.09), makePrice('ethanol', 3.99)],
      },
    );

    expect(comparison.rows).toHaveLength(2);
    const gasoline = comparison.rows.find((row) => row.fuel === 'gasoline');
    expect(gasoline).toMatchObject({
      comparable: true,
      difference: -0.2,
      cheaper: 'A',
      note: 'comparable',
    });
    expect(gasoline?.stationA.price?.amount).toBe(5.89);
    expect(gasoline?.stationB.price?.amount).toBe(6.09);

    const ethanol = comparison.rows.find((row) => row.fuel === 'ethanol');
    expect(ethanol).toMatchObject({ comparable: true, cheaper: 'B', note: 'comparable' });
    expect(ethanol?.difference).toBeCloseTo(0.11, 5);
  });

  it('arredonda a diferença para três casas decimais', () => {
    const comparison = buildComparison(
      { id: 'a', name: 'A', prices: [makePrice('gasoline', 5.891)] },
      { id: 'b', name: 'B', prices: [makePrice('gasoline', 5.9)] },
    );
    expect(comparison.rows[0]?.difference).toBe(-0.009);
  });

  it('marca empate quando os valores são iguais', () => {
    const comparison = buildComparison(
      { id: 'a', name: 'A', prices: [makePrice('diesel_s10', 6.49)] },
      { id: 'b', name: 'B', prices: [makePrice('diesel_s10', 6.49)] },
    );
    expect(comparison.rows[0]).toMatchObject({
      comparable: true,
      difference: 0,
      cheaper: 'tie',
      note: 'tie',
    });
  });

  it('não calcula diferença quando só um dos postos possui o preço', () => {
    const comparison = buildComparison(
      {
        id: 'a',
        name: 'A',
        prices: [makePrice('gasoline', 5.89), makePrice('gnv', 4.49)],
      },
      { id: 'b', name: 'B', prices: [makePrice('gasoline', 6.19)] },
    );

    const gnv = comparison.rows.find((row) => row.fuel === 'gnv');
    expect(gnv).toMatchObject({ comparable: false, note: 'only_a' });
    expect(gnv?.difference).toBeUndefined();
    expect(gnv?.cheaper).toBeUndefined();
    expect(gnv?.stationB.price).toBeUndefined();
  });

  it('identifica preço presente apenas no posto B', () => {
    const comparison = buildComparison(
      { id: 'a', name: 'A', prices: [] },
      { id: 'b', name: 'B', prices: [makePrice('ethanol', 3.79)] },
    );
    expect(comparison.rows).toHaveLength(1);
    expect(comparison.rows[0]).toMatchObject({ comparable: false, note: 'only_b' });
  });

  it('omite combustíveis sem dados nos dois postos e mantém a ordem canônica', () => {
    const comparison = buildComparison(
      { id: 'a', name: 'A', prices: [makePrice('gnv', 4.59), makePrice('gasoline', 5.99)] },
      { id: 'b', name: 'B', prices: [makePrice('ethanol', 4.09), makePrice('gasoline', 6.09)] },
    );
    expect(comparison.rows.map((row) => row.fuel)).toEqual(['gasoline', 'ethanol', 'gnv']);
    expect(comparison.stationA).toEqual({ id: 'a', name: 'A' });
    expect(comparison.stationB).toEqual({ id: 'b', name: 'B' });
  });

  it('retorna lista vazia quando nenhum posto tem preços', () => {
    const comparison = buildComparison(
      { id: 'a', name: 'A', prices: [] },
      { id: 'b', name: 'B', prices: [] },
    );
    expect(comparison.rows).toEqual([]);
  });
});

describe('collectRegionalReference', () => {
  it('seleciona a média municipal do combustível solicitado', () => {
    const prices = [makeRegionalPrice('gasoline', 6.1), makeRegionalPrice('ethanol', 4.2)];
    expect(collectRegionalReference(prices, 'ethanol')?.amount).toBe(4.2);
    expect(collectRegionalReference(prices, 'diesel_s10')).toBeUndefined();
  });
});
