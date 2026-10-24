# FuelCompare — Backend

API intermediária do **FuelCompare**, aplicativo Android para encontrar postos de combustível próximos e comparar preços no Brasil.

O backend existe para:

- falar com fontes externas sem expor detalhes de infraestrutura ao aplicativo;
- normalizar dados heterogêneos (OpenStreetMap, ANP e Nominatim);
- aplicar cache, limites de uso e timeouts;
- deixar explícita a diferença entre **preço individual pesquisado**, **média municipal** e **preço indisponível**;
- permitir que outras fontes de preço sejam adicionadas no futuro sem alterar o aplicativo.

> Importante: nenhum valor é inventado. Quando não há preço individual confiável para um posto, a lista `prices` do posto fica vazia. Médias municipais nunca são atribuídas a um posto específico.

A visão geral do repositório, o aplicativo Android e as instruções de execução estão no [README principal](../README.md).

## Stack

- Node.js 22+ (validado com Node 24)
- TypeScript (ESM, `NodeNext`, modo estrito)
- Express 5
- Zod 4.6.7 para validação
- Helmet, CORS opcional e `express-rate-limit`
- Pino / pino-http para logs estruturados
- ExcelJS 4.4.0 para ler os arquivos oficiais da ANP
- Vitest 5.0.5 + Supertest para testes
- Cache em memória com TTL, coalescência de requisições e fallback para dados expirados

## Como executar

```bash
cd fuelcompare-backend
npm ci
cp .env.example .env
npm run dev
```

A API sobe por padrão em `http://localhost:3333`.

Variáveis principais (todas documentadas em [`.env.example`](./.env.example)):

| Variável | Padrão | Descrição |
| --- | --- | --- |
| `PORT` | `3333` | Porta HTTP |
| `HTTP_USER_AGENT` | identificação do projeto | Exigida pelo Overpass e pelo Nominatim; em produção inclua um contato real |
| `OVERPASS_URL` | `https://overpass-api.de/api/interpreter` | Instância do Overpass |
| `NOMINATIM_URL` | `https://nominatim.openstreetmap.org` | Geocodificação |
| `NEARBY_CACHE_TTL_SECONDS` | `600` | TTL da busca por postos |
| `STATION_CACHE_TTL_SECONDS` | `1800` | TTL dos detalhes de um posto |
| `ANP_CACHE_TTL_SECONDS` | `21600` | TTL do levantamento semanal da ANP |
| `RATE_LIMIT_MAX` | `120` | Requisições por janela e IP |
| `GEOCODE_RATE_LIMIT_MAX` | `30` | Buscas de localização por janela e IP |
| `ENFORCE_HTTPS` | `false` | Em produção, rejeita requisições não HTTPS |
| `TRUST_PROXY` | `false` | Habilite apenas atrás de proxy reverso confiável |
| `CORS_ORIGINS` | vazio | Lista separada por vírgulas; vazio desliga o CORS |

**Segurança:** não há segredos no repositório. O arquivo `.env` é ignorado pelo Git. O `.env.example` contém apenas valores fictícios. Nenhuma chave é necessária para as fontes atuais.

### Scripts

```bash
npm run dev        # servidor com recarga
npm run typecheck  # TypeScript sem emitir
npm run build      # compila para dist/
npm start          # executa dist/server.js
npm test           # Vitest (unitários + integração)
npm run audit      # npm audit de dependências de produção
```

## Endpoints

Base: `/api/v1`. Todas as respostas usam UTF-8 e JSON.

| Método | Rota | Descrição |
| --- | --- | --- |
| GET | `/health` | Estado do processo e da base de preços da ANP |
| GET | `/stations/nearby` | Postos em um raio, com preços individuais quando houver associação |
| GET | `/stations/:id` | Detalhes de um posto + médias municipais |
| GET | `/stations/:id/prices` | Somente preços e fontes do posto |
| GET | `/prices/compare` | Comparação lado a lado de dois postos |
| GET | `/geocode/search` | Busca manual por município, CEP ou endereço |
| GET | `/geocode/reverse` | Município/UF a partir de coordenadas |
| GET | `/sources` | Atribuições e origem dos dados |

A especificação completa está em [`docs/openapi.yaml`](./docs/openapi.yaml).

### `GET /stations/nearby`

Parâmetros:

| Nome | Obrigatório | Limites | Padrão |
| --- | --- | --- | --- |
| `lat` | sim | -90 a 90 | — |
| `lon` | sim | -180 a 180 | — |
| `radiusKm` | não | 1 a 10 | 5 |
| `limit` | não | 1 a 100 | 50 |
| `city` | não | 2 a 100 caracteres | resolvido por geocodificação reversa |
| `state` | não | UF ou nome do estado | resolvido por geocodificação reversa |

Exemplo:

```bash
curl "http://localhost:3333/api/v1/stations/nearby?lat=-23.5505&lon=-46.6333&radiusKm=5&limit=20"
```

Resposta (estrutura):

```json
{
  "data": {
    "stations": [
      {
        "id": "way_367319792",
        "name": "Shell",
        "brand": "Shell",
        "address": {
          "street": "Avenida do Estado",
          "number": "5300",
          "city": "São Paulo",
          "postalCode": "01516-000",
          "label": "Avenida do Estado, 5300 · São Paulo"
        },
        "latitude": -23.55779,
        "longitude": -46.6189812,
        "distanceKm": 1.67,
        "prices": [
          {
            "fuel": "gasoline",
            "amount": 6.29,
            "currency": "BRL",
            "unit": "liter",
            "priceType": "individual",
            "source": {
              "name": "ANP - Levantamento de Preços de Combustíveis",
              "url": "https://www.gov.br/anp/.../revendas_lpc_2026_10_11-2026_10_17.xlsx",
              "official": true
            },
            "referenceDate": "2026-10-12",
            "collectedAt": "2026-10-12T12:00:00.000Z",
            "validationStatus": "official_survey",
            "matchMethod": "address"
          }
        ]
      }
    ],
    "regionalPrices": [
      {
        "fuel": "gasoline",
        "amount": 6.34,
        "minAmount": 5.49,
        "maxAmount": 8.49,
        "currency": "BRL",
        "unit": "liter",
        "priceType": "municipal_average",
        "municipality": "Sao Paulo",
        "state": "SP",
        "referenceStart": "2026-10-11",
        "referenceEnd": "2026-10-17",
        "surveyedStations": 120,
        "source": { "name": "ANP - Levantamento de Preços de Combustíveis", "official": true }
      }
    ],
    "region": { "municipality": "São Paulo", "state": "SP", "resolvedFrom": "geocoder" },
    "lastUpdated": "2026-10-17T12:00:00.000Z",
    "sources": []
  },
  "meta": {
    "cached": false,
    "generatedAt": "2026-10-24T12:00:00.000Z",
    "warnings": []
  }
}
```

Os valores acima refletem uma resposta real do período **11–17/10/2026** usada durante o desenvolvimento. Eles não representam preços atuais.

Regras importantes:

- `prices` contém **apenas** preços individuais (`priceType: "individual"`).
- `regionalPrices` contém **apenas** médias municipais (`priceType: "municipal_average"`).
- `matchMethod` informa como o posto do OSM foi associado à ANP: `cnpj`, `address` ou `name`.
- Postos sem associação ficam com `prices: []`; nenhum valor é preenchido por estimativa.
- `distanceKm` é calculado pelo backend a partir do ponto consultado.
- Se a ANP ainda não carregou, a resposta continua válida com `regionalPrices: []` e um aviso em `meta.warnings`.

### `GET /prices/compare`

Parâmetros: `stationA` e `stationB`, ambos no formato `node_123`, `way_123` ou `relation_123`, distintos.

Para cada combustível presente em pelo menos um posto:

- `comparable: true`, `difference` (A − B, arredondada para 3 casas) e `cheaper: "A" | "B" | "tie"`;
- `comparable: false` com `note: "only_a"` ou `"only_b"`, sem diferença calculada;
- combustíveis ausentes nos dois postos não aparecem.

### Erros

Formato único:

```json
{
  "error": {
    "code": "INVALID_PARAMETERS",
    "message": "Parâmetros de busca inválidos.",
    "details": [{ "field": "radiusKm", "message": "Number must be less than or equal to 10" }],
    "requestId": "uuid"
  }
}
```

- `400 INVALID_PARAMETERS`: parâmetros inválidos.
- `404 NOT_FOUND`: posto ou rota inexistente.
- `429 RATE_LIMITED`: limite de requisições; inclui cabeçalhos padrão de rate limit.
- `502 UPSTREAM_UNAVAILABLE`: Overpass/Nominatim indisponível.
- `504 UPSTREAM_TIMEOUT`: fonte externa excedeu o tempo limite.
- `500 INTERNAL_ERROR`: erro inesperado, sem stack trace ou detalhes internos na resposta.

## Fontes de dados

### OpenStreetMap / Overpass

- Consulta `nwr["amenity"="fuel"]` em um raio, com `out center tags`.
- Limita raio a 10 km e quantidade a 100 postos.
- Exige `User-Agent` identificável, conforme as políticas dos serviços públicos.
- A disponibilidade do Overpass é variável; o servidor responde com erro controlado e, quando já existe cache, devolve a última resposta válida com aviso.
- Atribuição: dados © colaboradores do OpenStreetMap, licença [ODbL](https://opendatacommons.org/licenses/odbl/).

Referência: [política de uso do Overpass](https://wiki.openstreetmap.org/wiki/Overpass_API#Usage_policy).

### Nominatim

- Usado para busca manual e geocodificação reversa.
- Cache de 24 h para buscas e 7 dias para reverso.
- Requisições serializadas com intervalo mínimo de 1,1 s, respeitando o limite de 1 req/s.
- `User-Agent` identificável obrigatório.
- Atribuição: geocodificação baseada no OpenStreetMap/Nominatim.

Referência: [política de uso do Nominatim](https://operations.osmfoundation.org/policies/nominatim/).

### ANP

Investigação realizada em outubro/2026:

- `https://revendedoresapi.anp.gov.br` expõe apenas dados cadastrais (`/v1/combustivel` e `/v1/glp`), **sem coordenadas e sem preços**. Não foi usado como fonte de preço.
- Os preços vêm dos arquivos semanais oficiais de Levantamento de Preços de Combustíveis (LPC):
  - **preços individuais:** `revendas_lpc_AAAA_MM_DD-AAAA_MM_DD.xlsx`;
  - **médias municipais:** `resumo_semanal_lpc_AAAA_MM_DD-AAAA_MM_DD.xlsx`.
- O backend descobre os arquivos mais recentes na página oficial da ANP, com TTL de 6 h. Também é possível fixar as URLs por variável de ambiente.
- Base validada em 24/10/2026: período **11–17/10/2026**, **4.588** estabelecimentos com preços individuais e **2.071** médias municipais carregadas.

#### Como um preço individual é associado a um posto do OSM

A associação é conservadora e usa, em ordem:

1. **CNPJ** presente em tags do OSM (`ref:vatin`, `ref:BR:CNPJ`, `cnpj` etc.), quando existir;
2. **endereço completo**: logradouro normalizado + número + município + UF, e somente se houver **um único** posto da ANP com essa chave;
3. **nome distintivo**: nome normalizado + município + UF, somente se for único. Nomes que são apenas a bandeira (“Shell”, “Ipiranga”) são recusados.

A UF ausente em um endereço do OSM é preenchida com a UF resolvida para a região da busca. Se não houver correspondência unívoca, o posto fica sem preço. Isso explica por que muitos postos exibem `prices: []` no mapa: a amostra semanal da ANP não cobre todos os postos e nem todo endereço do OSM é comparável.

Médias municipais são mantidas em uma coleção separada e **nunca** são associadas a um posto. A API também não mistura as duas categorias em cálculos.

Referências:

- [Levantamento de Preços de Combustíveis — ANP](https://www.gov.br/anp/pt-br/assuntos/precos-e-defesa-da-concorrencia/precos/levantamento-de-precos-de-combustiveis-ultimas-semanas-pesquisadas)
- [Dados abertos da ANP](https://www.gov.br/anp/pt-br/assuntos/precos-e-defesa-da-concorrencia/precos)

## Arquitetura

```text
fuelcompare-backend/
├── src/
│   ├── config/env.ts              # validação de ambiente
│   ├── controllers/               # HTTP → serviços
│   ├── middlewares/errorHandler.ts
│   ├── providers/
│   │   ├── overpass/              # postos do OSM
│   │   ├── nominatim/             # geocodificação
│   │   └── anp/                   # descoberta, download, parsing e associação
│   ├── schemas/apiSchemas.ts      # Zod + limites
│   ├── services/
│   │   ├── stationService.ts      # orquestração + caches
│   │   ├── geocodingService.ts
│   │   └── comparisonService.ts
│   ├── utils/                     # cache, HTTP, erros, geo, texto, log
│   ├── app.ts                     # middlewares, rotas, rate limit
│   ├── container.ts               # composição das dependências
│   └── server.ts                  # inicialização e encerramento gracioso
├── tests/
│   ├── unit/
│   └── integration/
├── docs/openapi.yaml
├── .env.example
├── vitest.config.ts
└── package.json
```

O cache em memória (`TtlCache`) possui a mesma interface necessária para uma futura implementação Redis: `get`, `peek`, `set`, `getOrLoad`, `delete`, `clear`, `stats`.

## Segurança aplicada

- Helmet com CSP, `nosniff`, `no-referrer`; HSTS habilitado quando `NODE_ENV=production`.
- CORS desligado por padrão; quando configurado, aceita apenas as origens da lista.
- `express.json` limitado a 10 kB.
- Rate limiting global e mais restrito para geocodificação.
- Validação estrita de query strings, com rejeição de parâmetros desconhecidos.
- Limites de raio, quantidade de resultados e tamanho de downloads (5 MB Overpass; 15/25 MB ANP).
- Timeouts e tentativas controladas em todas as chamadas externas.
- Erros públicos sem stack trace, caminho de arquivo, SQL ou segredo. O stack fica apenas no log do servidor.
- Logs com `requestId`, método e rota sem query string; redação de `authorization` e `cookie`.
- `TRUST_PROXY` e `ENFORCE_HTTPS` explícitos para produção.
- Nenhuma credencial ou chave no repositório.
- Encerramento gracioso com limite de 10 s.

## Dependências e auditoria

- `zod` foi atualizado para `^4.6.7` e `vitest` para `^5.0.5` para corrigir vulnerabilidades reportadas pelo npm.
- O campo `overrides` fixa `uuid` em `^11.1.1`, removendo uma versão transitiva vulnerável trazida pelo ExcelJS.
- Em 24/10/2026, `npm audit --omit=dev --audit-level=high` e `npm audit` completo retornaram **0 vulnerabilidades**.
- A CI executa typecheck, testes, build e auditoria de dependências de produção a cada push/PR. O Dependabot do repositório verifica semanalmente Gradle, npm e GitHub Actions.
- O `package-lock.json` deve ser mantido versionado; o `node_modules` não é versionado.

## Testes

```bash
npm test
```

Resultado da última execução (24/10/2026): **9 arquivos, 85 testes aprovados**.

Cobrem:

- cache: TTL, fallback, coalescência, limite de entradas e falhas;
- comparação: diferenças, empates, preços ausentes e ordem dos combustíveis;
- associação ANP: CNPJ, endereço normalizado, ambiguidades, nome e bandeira;
- parsing real das planilhas (geradas em memória com o mesmo formato): preços, datas, período, deduplicação;
- validação de parâmetros, sanitização e limites;
- serviço de postos: raio, ordenação, cache, indisponibilidade, preços individuais vs. médias;
- API: health 200/503, respostas 400/404/429/500, cabeçalhos de segurança, geocodificação e comparação;
- sanitização das respostas de erro.

Validação manual em execução real:

- `GET /api/v1/health` → `200`, ANP carregada com 4.588 postos e 2.071 médias.
- `GET /api/v1/stations/nearby` em São Paulo → `200` com postos do OSM, preços individuais associados e médias municipais separadas; resposta em cache em ~0,2 s.
- Durante uma indisponibilidade do Overpass, a API respondeu `502` controlado, sem vazar detalhes internos, em vez de travar ou retornar dados falsos.

## Limitações conhecidas

- A ANP não fornece preço em tempo real por posto; os dados são semanais e de uma amostra de estabelecimentos.
- O OSM não é um cadastro comercial: nome, bandeira, endereço e número podem faltar ou estar desatualizados.
- A associação OSM↔ANP depende de endereço/CNPJ e cobre apenas parte dos postos exibidos. Postos sem correspondência aparecem sem preço, por decisão de integridade.
- O backend não geocodifica em massa postos da ANP (isso violaria o uso justo do Nominatim). Por isso, postos da ANP sem correspondência de endereço não são plotados a partir do preço.
- A disponibilidade pública do Overpass varia; em picos, a busca pode retornar 502 até o serviço normalizar.
- Não há banco de dados persistente no backend; o cache é em memória e é perdido ao reiniciar. Para múltiplas instâncias, recomenda-se Redis.
- Contribuições de usuários ainda não estão implementadas; o modelo `StationPrice` e o campo de validação já preveem a evolução com moderação e `priceType` próprio.

## Próximos passos

1. Cache distribuído (Redis) e histórico de preços.
2. Contribuição comunitária com moderação, limite por usuário e detecção de abuso.
3. Painel administrativo leve para revisão de associações duvidosas.
4. Métricas e alertas de disponibilidade dos provedores.
5. OpenAPI gerado a partir dos schemas Zod para evitar divergência.

## Licença e atribuições

Código do projeto: uso educacional/portfólio. Os dados mantêm as licenças de suas fontes:

- © colaboradores do OpenStreetMap — ODbL.
- Nominatim — dados derivados do OpenStreetMap.
- ANP — dados públicos oficiais, conforme termos do portal gov.br.
