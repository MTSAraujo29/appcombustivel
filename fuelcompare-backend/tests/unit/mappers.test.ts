import { describe, expect, it } from 'vitest';
import { mapOverpassElements } from '../../src/providers/overpass/overpassMapper.js';
import {
  mapReverseResult,
  mapSearchResults,
} from '../../src/providers/nominatim/nominatimMapper.js';

describe('mapOverpassElements', () => {
  it('mapeia nós e vias com centro, ignorando elementos que não são postos', () => {
    const stations = mapOverpassElements([
      {
        type: 'node',
        id: 1,
        lat: -23.55,
        lon: -46.63,
        tags: {
          amenity: 'fuel',
          name: 'Posto Central',
          brand: 'Shell',
          'addr:street': 'Avenida Paulista',
          'addr:housenumber': '1000',
          'addr:suburb': 'Bela Vista',
          'addr:city': 'São Paulo',
          'addr:state': 'SP',
          'addr:postcode': '01310-100',
        },
      },
      {
        type: 'way',
        id: 2,
        center: { lat: -23.56, lon: -46.64 },
        tags: { amenity: 'fuel', operator: 'Ipiranga' },
      },
      {
        type: 'node',
        id: 3,
        lat: -23.5,
        lon: -46.6,
        tags: { amenity: 'parking' },
      },
      {
        type: 'way',
        id: 4,
        tags: { amenity: 'fuel' },
      },
    ]);

    expect(stations).toHaveLength(2);
    expect(stations[0]).toMatchObject({
      id: 'node_1',
      name: 'Posto Central',
      brand: 'Shell',
      latitude: -23.55,
      longitude: -46.63,
      address: {
        street: 'Avenida Paulista',
        number: '1000',
        neighborhood: 'Bela Vista',
        city: 'São Paulo',
        state: 'SP',
        postalCode: '01310-100',
        label: 'Avenida Paulista, 1000 · Bela Vista · São Paulo - SP',
      },
    });
    expect(stations[1]).toMatchObject({
      id: 'way_2',
      name: 'Ipiranga',
      brand: 'Ipiranga',
      latitude: -23.56,
      longitude: -46.64,
    });
  });

  it('usa um nome padrão quando o posto não possui nome nem marca', () => {
    const [station] = mapOverpassElements([
      { type: 'node', id: 9, lat: -1, lon: -2, tags: { amenity: 'fuel' } },
    ]);
    expect(station?.name).toBe('Posto de combustível');
    expect(station?.brand).toBeUndefined();
  });
});

describe('mapSearchResults', () => {
  it('mapeia sugestões de localização com município e UF', () => {
    const suggestions = mapSearchResults(
      [
        {
          osm_type: 'relation',
          osm_id: 298285,
          lat: '-23.5505',
          lon: '-46.6333',
          display_name: 'São Paulo, Região Sudeste, Brasil',
          address: {
            city: 'São Paulo',
            state: 'São Paulo',
            postcode: '01000-000',
            'ISO3166-2-lvl4': 'BR-SP',
          },
        },
      ],
      5,
    );
    expect(suggestions).toEqual([
      {
        id: 'relation_298285',
        displayName: 'São Paulo, Região Sudeste, Brasil',
        municipality: 'São Paulo',
        state: 'SP',
        latitude: -23.5505,
        longitude: -46.6333,
        postalCode: '01000-000',
      },
    ]);
  });

  it('ignora resultados sem município, sem UF ou duplicados', () => {
    const suggestions = mapSearchResults(
      [
        {
          osm_type: 'node',
          osm_id: 1,
          lat: '-23.55',
          lon: '-46.63',
          display_name: 'Sem município',
          address: { state: 'SP' },
        },
        {
          osm_type: 'node',
          osm_id: 2,
          lat: '-23.55',
          lon: '-46.63',
          display_name: 'Sem UF',
          address: { city: 'São Paulo' },
        },
        {
          osm_type: 'node',
          osm_id: 3,
          lat: '-23.55',
          lon: '-46.63',
          display_name: 'São Paulo',
          address: { city: 'São Paulo', state: 'SP' },
        },
        {
          osm_type: 'node',
          osm_id: 4,
          lat: '-23.55',
          lon: '-46.63',
          display_name: 'São Paulo duplicado',
          address: { city: 'São Paulo', state: 'SP' },
        },
      ],
      5,
    );
    expect(suggestions).toHaveLength(1);
    expect(suggestions[0]?.id).toBe('node_3');
  });

  it('respeita o limite de sugestões', () => {
    const results = Array.from({ length: 5 }, (_, index) => ({
      osm_type: 'node',
      osm_id: index + 1,
      lat: String(-23.5 - index),
      lon: '-46.6',
      display_name: `Cidade ${index}`,
      address: { city: `Cidade ${index}`, state: 'SP' },
    }));
    expect(mapSearchResults(results, 2)).toHaveLength(2);
  });
});

describe('mapReverseResult', () => {
  it('mapeia o município e a UF da geocodificação reversa', () => {
    const region = mapReverseResult({
      lat: '-23.55',
      lon: '-46.63',
      address: { city: 'São Paulo', state: 'São Paulo', 'ISO3166-2-lvl4': 'BR-SP' },
    });
    expect(region).toEqual({
      municipality: 'São Paulo',
      state: 'SP',
      latitude: -23.55,
      longitude: -46.63,
    });
  });

  it('retorna nulo para resposta vazia ou incompleta', () => {
    expect(mapReverseResult(null)).toBeNull();
    expect(mapReverseResult({ lat: '-23.55', lon: '-46.63', address: { state: 'SP' } })).toBeNull();
    expect(
      mapReverseResult({ lat: 'inválido', lon: '-46.63', address: { city: 'X', state: 'SP' } }),
    ).toBeNull();
  });
});
