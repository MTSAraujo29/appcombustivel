import { describe, expect, it } from 'vitest';
import type { AnpStationEntry } from '../../src/providers/anp/anpParser.js';
import {
  buildAddressKey,
  buildAnpIndex,
  buildNameKey,
  matchAnpStation,
} from '../../src/providers/anp/anpMatcher.js';

function entry(overrides: Partial<AnpStationEntry> & { cnpj: string }): AnpStationEntry {
  return {
    fantasyName: 'Auto Posto Central',
    street: 'Avenida Paulista',
    number: '1000',
    municipality: 'Sao Paulo',
    state: 'SP',
    products: new Map(),
    ...overrides,
  };
}

describe('chaves de associação da ANP', () => {
  it('normaliza endereço, número, município e UF', () => {
    expect(
      buildAddressKey('Av. Paulista', 'Nº 1000', 'São Paulo', 'São Paulo'),
    ).toBe('avenida paulista|1000|sao paulo|SP');
  });

  it('rejeita endereço incompleto, especialmente sem número', () => {
    expect(buildAddressKey('Avenida Paulista', undefined, 'São Paulo', 'SP')).toBe('');
    expect(buildAddressKey(undefined, '1000', 'São Paulo', 'SP')).toBe('');
    expect(buildAddressKey('Avenida Paulista', '1000', undefined, 'SP')).toBe('');
    expect(buildAddressKey('Avenida Paulista', 'S/N', 'São Paulo', 'SP')).toBe('');
  });

  it('remove termos genéricos do nome ao montar a chave', () => {
    expect(buildNameKey('Auto Posto Central LTDA', 'São Paulo', 'SP')).toBe('central|sao paulo|SP');
    expect(buildNameKey('Posto Ipiranga', 'São Paulo', 'SP')).toBe('ipiranga|sao paulo|SP');
    expect(buildNameKey('A', 'São Paulo', 'SP')).toBe('');
  });
});

describe('matchAnpStation', () => {
  it('prioriza a associação por CNPJ, aceitando pontuação', () => {
    const station = entry({
      cnpj: '11222333000181',
      street: 'Outra Rua',
      number: '50',
    });
    const index = buildAnpIndex([station]);
    const match = matchAnpStation(index, { cnpj: '11.222.333/0001-81' });
    expect(match).toMatchObject({ method: 'cnpj' });
    expect(match?.entry).toBe(station);
  });

  it('associa por endereço completo normalizado', () => {
    const station = entry({ cnpj: '11222333000181' });
    const index = buildAnpIndex([station]);
    const match = matchAnpStation(index, {
      street: 'Av. Paulista',
      number: '1000',
      city: 'São Paulo',
      state: 'São Paulo',
    });
    expect(match).toMatchObject({ method: 'address' });
    expect(match?.entry.cnpj).toBe('11222333000181');
  });

  it('recusa endereço ambíguo e tenta nome apenas se ele for único', () => {
    const first = entry({ cnpj: '11222333000181', fantasyName: 'Posto Central' });
    const second = entry({ cnpj: '99888777000166', fantasyName: 'Posto Central' });
    const index = buildAnpIndex([first, second]);

    expect(
      matchAnpStation(index, {
        street: 'Avenida Paulista',
        number: '1000',
        city: 'São Paulo',
        state: 'SP',
      }),
    ).toBeNull();

    expect(
      matchAnpStation(index, {
        name: 'Auto Posto Central',
        city: 'São Paulo',
        state: 'SP',
      }),
    ).toBeNull();
  });

  it('associa por nome distintivo quando não há endereço utilizável', () => {
    const station = entry({
      cnpj: '11222333000181',
      fantasyName: 'Posto Estrela do Sul',
      street: undefined,
      number: undefined,
    });
    const index = buildAnpIndex([station]);
    const match = matchAnpStation(index, {
      name: 'Auto Posto Estrela do Sul LTDA',
      city: 'São Paulo',
      state: 'SP',
    });
    expect(match).toMatchObject({ method: 'name' });
  });

  it('não associa quando o nome do posto é apenas a bandeira', () => {
    const station = entry({
      cnpj: '11222333000181',
      fantasyName: 'Shell',
      street: undefined,
      number: undefined,
    });
    const index = buildAnpIndex([station]);
    expect(
      matchAnpStation(index, { name: 'Shell', brand: 'Shell', city: 'São Paulo', state: 'SP' }),
    ).toBeNull();
  });

  it('não associa quando faltam município ou UF para desambiguar', () => {
    const station = entry({
      cnpj: '11222333000181',
      fantasyName: 'Posto Estrela do Sul',
      street: undefined,
      number: undefined,
    });
    const index = buildAnpIndex([station]);
    expect(matchAnpStation(index, { name: 'Posto Estrela do Sul' })).toBeNull();
  });

  it('retorna nulo para entrada sem nenhum dado aproveitável', () => {
    const station = entry({ cnpj: '11222333000181' });
    const index = buildAnpIndex([station]);
    expect(matchAnpStation(index, {})).toBeNull();
  });
});
