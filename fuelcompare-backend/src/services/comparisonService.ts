import {
  FUEL_CODES,
  type FuelCode,
  type PriceComparison,
  type PriceComparisonRow,
  type RegionalPrice,
  type Station,
  type StationPrice,
} from '../types.js';

function findPrice(prices: StationPrice[], fuel: FuelCode): StationPrice | undefined {
  return prices.find((price) => price.fuel === fuel);
}

function roundAmount(value: number): number {
  return Math.round(value * 1000) / 1000;
}

export function buildComparison(
  stationA: Pick<Station, 'id' | 'name' | 'prices'>,
  stationB: Pick<Station, 'id' | 'name' | 'prices'>,
): PriceComparison {
  const rows: PriceComparisonRow[] = [];

  for (const fuel of FUEL_CODES) {
    const priceA = findPrice(stationA.prices, fuel);
    const priceB = findPrice(stationB.prices, fuel);
    if (!priceA && !priceB) continue;

    const base = {
      fuel,
      stationA: {
        stationId: stationA.id,
        stationName: stationA.name,
        ...(priceA ? { price: priceA } : {}),
      },
      stationB: {
        stationId: stationB.id,
        stationName: stationB.name,
        ...(priceB ? { price: priceB } : {}),
      },
    };

    if (priceA && priceB) {
      const difference = roundAmount(priceA.amount - priceB.amount);
      const cheaper = difference === 0 ? 'tie' : difference < 0 ? 'A' : 'B';
      rows.push({
        ...base,
        comparable: true,
        difference,
        cheaper,
        note: difference === 0 ? 'tie' : 'comparable',
      });
    } else if (priceA) {
      rows.push({ ...base, comparable: false, note: 'only_a' });
    } else {
      rows.push({ ...base, comparable: false, note: 'only_b' });
    }
  }

  return {
    stationA: { id: stationA.id, name: stationA.name },
    stationB: { id: stationB.id, name: stationB.name },
    rows,
  };
}

export function collectRegionalReference(
  prices: RegionalPrice[],
  fuel: FuelCode,
): RegionalPrice | undefined {
  return prices.find((price) => price.fuel === fuel);
}
