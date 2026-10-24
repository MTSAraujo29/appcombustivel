# FuelCompare

Aplicativo Android nativo para encontrar postos de combustível próximos e comparar **preços individuais** publicados pela ANP, com mapa e lista baseados no OpenStreetMap. O projeto inclui o aplicativo em Kotlin/Jetpack Compose e um backend próprio em Node.js/TypeScript que integra as fontes externas, normaliza os dados e mantém o aplicativo protegido de detalhes de infraestrutura.

**Status:** MVP funcional, compilado e testado localmente em 24/10/2026. O aplicativo não é um serviço de preços em tempo real e não substitui a conferência no posto.

- Documentação da API: [fuelcompare-backend/README.md](fuelcompare-backend/README.md) e [OpenAPI](fuelcompare-backend/docs/openapi.yaml)
- Configuração de ambiente de exemplo: [fuelcompare-backend/.env.example](fuelcompare-backend/.env.example)

## Integridade dos dados

Esta é uma decisão central do projeto:

- O aplicativo só destaca ou compara **preços individuais plausíveis e recentes**.
- A **média municipal** da ANP é exibida separadamente, com a semana de referência, e **nunca** é atribuída a um posto específico.
- Um posto sem preço individual correspondente aparece como **indisponível**; nenhum valor é estimado, interpolado ou preenchido para "completar" a tela.
- Dados recuperados do cache local são identificados como salvos, com a data real do snapshot.
- A data de referência do preço acompanha o valor nas telas e na API.

## Funcionalidades

### Onboarding

- Apresentação do app e do uso de localização aproximada.
- Permissão **coarse** (`ACCESS_COARSE_LOCATION`), solicitada somente por ação explícita.
- Busca manual por cidade, CEP ou endereço, com sugestões do Nominatim; funciona sem permissão de localização.
- Conclusão do onboarding persistida em DataStore.

### Tela inicial

- Mapa real com osmdroid e tiles do OpenStreetMap, sem chave paga.
- Alternância entre mapa e lista.
- Filtros de combustível: gasolina comum, gasolina aditivada, etanol, diesel S10, diesel comum e GNV.
- Ordenação por preço, distância ou nome.
- Destaque visual e acessível para o menor preço elegível da área, com data de referência.
- Cartão de melhor resultado com atalhos para abrir o posto, comparar manualmente ou comparar os dois postos mais baratos quando existirem pelo menos dois preços individuais recentes.
- Cartão separado de médias municipais, com faixa de preço, quantidade de postos pesquisados e semana de referência.
- Indicador de última atualização, aviso de dados salvos, fallback offline e estados de carregamento, erro, vazio, sem permissão e atualização.
- Pull-to-refresh e cache local via Room.
- Seletor de raio de busca entre 1 e 10 km e de combustível padrão.

### Detalhes do posto

- Todos os combustíveis conhecidos, com preço individual quando disponível.
- Origem, data de referência e indicação de preço desatualizado.
- Aviso explícito quando não há preço individual para um combustível.
- Médias municipais separadas, deixando claro que não são o preço do posto.
- Fontes, atribuições e data da última atualização.
- Abertura de rota pelo intent `geo:`; se não houver aplicativo compatível, o app informa a limitação em vez de falhar silenciosamente.

### Comparação

- Comparação manual de dois postos, escolhidos a partir da região carregada.
- Comparação automática dos dois postos mais baratos elegíveis, disponível somente quando há dois preços individuais válidos.
- Diferença calculada apenas quando os dois postos têm preço para o mesmo combustível; quando só um lado tem preço, a tela informa isso e não calcula diferença.
- Comparação refeita localmente com snapshots salvos quando o backend está indisponível, usando a data do snapshot mais antigo.
- Acesso aos detalhes de cada posto comparado.

### Configurações

- Tema claro, escuro ou do sistema.
- Combustível padrão e raio de busca.
- Limpeza do cache local, com confirmação e tratamento de erro.
- Informações de privacidade, fontes, versão do app e aviso de que os preços podem variar.

### Comportamento offline

- A busca de postos e os detalhes são persistidos em Room.
- Falhas de rede, timeout e indisponibilidade do serviço usam o último snapshot válido quando existir, com data e aviso de cache.
- Sem dados salvos, o app mostra um erro amigável e permite tentar novamente.
- Preferências e conclusão do onboarding são mantidas em DataStore.

## Arquitetura

### Visão geral

```text
┌──────────────────────────┐        HTTPS/JSON        ┌────────────────────────────┐
│ Aplicativo Android       │  ──────────────────────▶ │ Backend Node.js/TypeScript │
│ Compose + MVVM/UDF       │ ◀──────────────────────  │ API REST /api/v1           │
│ Room + DataStore         │                          │ cache em memória com TTL   │
└───────┬──────────────────┘                          └───────────┬────────────────┘
        │ tiles (HTTPS)                                             │
        ▼                                                           ├── OpenStreetMap / Overpass
OpenStreetMap (osmdroid)                                            ├── Nominatim
                                                                    └── ANP (XLSX semanal)
```

### Camadas do Android

O app usa Clean Architecture com MVVM/UDF e módulos separados:

- **`domain`**: Kotlin puro. Modelos, interfaces de repositório, casos de uso e regras de preço (plausibilidade, janela de atualidade, menor preço, ordenação e comparação).
- **`data`**: implementações dos repositórios, mapeadores, cache Room, DataStore, localização e tratamento de falhas de rede.
- **`core:network`**: contrato Retrofit, DTOs Kotlin Serialization e política de timeouts.
- **`core:database`**: banco Room, entidades e DAOs de snapshots.
- **`core:common`**: formatadores pt-BR/BRL e abstração de dispatchers.
- **`core:designsystem`**: tema Material 3, componentes de preço, estados, banners e atribuição.
- **`feature:*`**: `onboarding`, `home`, `stationdetails`, `comparison` e `settings`, cada uma com tela e ViewModel.
- **`app`**: `MainActivity`, navegação, tema raiz, injeção de dependências Hilt e composição dos módulos.

As dependências apontam para dentro: as features conhecem `domain`, `core:common` e `core:designsystem`; a implementação concreta de dados é injetada na composição do app com Hilt. O `domain` não depende de Android, Room, Retrofit ou Hilt.

### Backend

O backend isola os provedores externos, aplica validação Zod, rate limiting, timeouts, cache com TTL e respostas de erro centralizadas. A integração com a ANP:

1. descobre os arquivos semanais mais recentes na página oficial;
2. baixa e interpreta as planilhas com ExcelJS;
3. associa preços individuais a postos do OSM por CNPJ, endereço normalizado ou nome distintivo, sempre de forma conservadora;
4. mantém médias municipais em coleção separada;
5. nunca geocodifica em massa os postos da ANP nem usa scraping de páginas não documentadas.

Detalhes de endpoints, variáveis, segurança e limitações: [fuelcompare-backend/README.md](fuelcompare-backend/README.md).

#### API REST (resumo)

Todas as rotas usam JSON, UTF-8 e o prefixo `/api/v1`, exceto quando indicado:

| Método | Rota | Uso |
| --- | --- | --- |
| GET | `/api/v1/health` | Estado do processo e da base ANP |
| GET | `/api/v1/stations/nearby` | Postos em um raio, com preços individuais quando houver associação |
| GET | `/api/v1/stations/:id` | Detalhes do posto e médias municipais separadas |
| GET | `/api/v1/stations/:id/prices` | Preços e fontes do posto |
| GET | `/api/v1/prices/compare` | Comparação de dois postos |
| GET | `/api/v1/geocode/search` | Busca manual por cidade, CEP ou endereço |
| GET | `/api/v1/geocode/reverse` | Município/UF a partir de coordenadas |
| GET | `/api/v1/sources` | Atribuições e origem dos dados |

A especificação completa, com parâmetros, limites e formatos de erro, está em [OpenAPI](fuelcompare-backend/docs/openapi.yaml).

## Estrutura do repositório

```text
.
├── app/                         # aplicativo, navegação, Hilt, tema raiz
├── core/
│   ├── common/                  # formatadores e dispatchers
│   ├── designsystem/            # componente e tema Material 3
│   ├── network/                 # Retrofit, DTOs e falhas de rede
│   └── database/                # Room, entidades e DAOs
├── domain/                      # modelos, contratos, casos de uso e regras
├── data/                        # repositórios, cache, DataStore e localização
├── feature/
│   ├── onboarding/
│   ├── home/
│   ├── stationdetails/
│   ├── comparison/
│   └── settings/
├── fuelcompare-backend/
│   ├── src/                     # API, provedores, serviços, cache e utilidades
│   ├── tests/                   # testes unitários e de integração
│   ├── docs/openapi.yaml        # especificação da API
│   ├── .env.example             # configuração fictícia
│   └── README.md                # documentação detalhada do backend
├── .github/
│   ├── workflows/ci.yml         # CI de backend e Android
│   └── dependabot.yml           # atualizações semanais
├── gradle/libs.versions.toml    # catálogo de versões
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Tecnologias

### Android

| Item | Versão / escolha |
| --- | --- |
| Linguagem | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 (BOM 2024.12.01) |
| Arquitetura | Clean Architecture, MVVM, UDF, Coroutines/Flow |
| DI | Hilt 2.52 + KSP |
| Navegação | Navigation Compose 2.8.5 |
| HTTP | Retrofit 2.11.0 + OkHttp 4.12.0 + Kotlin Serialization 1.7.3 |
| Persistência | Room 2.6.1 + DataStore Preferences 1.1.1 |
| Localização | Google Play Services Location 21.3.0, somente coarse |
| Mapa | osmdroid 6.1.20, MAPNIK/OpenStreetMap |
| Build | AGP 8.7.3, Gradle 8.11.1, JDK 17, compileSdk/targetSdk 35, minSdk 26 |
| Testes | JUnit 4, MockK, Turbine, Truth, Compose UI Test |

### Backend

| Item | Versão / escolha |
| --- | --- |
| Runtime | Node.js 22+ (validado com Node 24) |
| Linguagem | TypeScript ESM estrito |
| HTTP | Express 5 |
| Validação | Zod 4.6.7 |
| Segurança | Helmet, express-rate-limit, CORS restritivo, validação estrita |
| Logs | Pino + pino-http com redação |
| Planilhas | ExcelJS 4.4.0 |
| Testes | Vitest 5.0.5 + Supertest |
| Cache | TTL em memória com coalescência e fallback controlado |

As versões acima são as efetivamente usadas no catálogo Gradle e no `package-lock.json`. O repositório não usa chaves pagas de mapas, autenticação de usuário ou banco de dados externo.

## Pré-requisitos

### Android

- Android Studio recente com JDK 17 (o JDK embutido do Android Studio é o caminho mais simples).
- Android SDK:
  - SDK Platform 35;
  - Build Tools compatíveis;
  - Android Emulator com uma imagem API 35 ou superior.
- Conexão com a internet para baixar dependências Gradle e tiles do mapa.
- Backend local ou uma URL HTTPS da API para o app funcionar.

O build também foi executado em Windows com JDK 21 como JVM do Gradle; o bytecode e a compilação Kotlin continuam configurados para Java 17.

### Backend

- Node.js 22 ou superior.
- npm.
- Acesso à internet para OpenStreetMap/Overpass, Nominatim e ANP.

## Como executar o backend localmente

Na raiz do projeto:

```bash
cd fuelcompare-backend
npm ci
cp .env.example .env
npm run dev
```

No Windows PowerShell, a cópia pode ser feita com:

```powershell
Copy-Item .env.example .env
```

A API sobe por padrão em `http://localhost:3333`. Verifique:

```bash
curl http://localhost:3333/api/v1/health
```

A primeira consulta de postos pode demorar porque envolve Overpass e, na inicialização, o download e o parsing das planilhas semanais da ANP. Em medições locais, a busca inicial levou cerca de 26 a 32 segundos; respostas em cache ficaram próximas de 0,2 segundo. O aplicativo usa timeout de leitura de 30 segundos, então a primeira consulta pode falhar e cair no cache local ou mostrar um erro amigável dependendo da disponibilidade externa.

Variáveis essenciais (a lista completa está em [`.env.example`](fuelcompare-backend/.env.example)):

| Variável | Padrão | Uso |
| --- | --- | --- |
| `PORT` | `3333` | Porta HTTP local |
| `HTTP_USER_AGENT` | identificação fictícia do projeto | Overpass e Nominatim exigem identificação; troque por um contato real em produção |
| `OVERPASS_URL` | `https://overpass-api.de/api/interpreter` | Consulta de postos do OSM |
| `NOMINATIM_URL` | `https://nominatim.openstreetmap.org` | Busca e geocodificação reversa |
| `NEARBY_CACHE_TTL_SECONDS` | `600` | Cache da busca próxima |
| `STATION_CACHE_TTL_SECONDS` | `1800` | Cache dos detalhes |
| `ANP_CACHE_TTL_SECONDS` | `21600` | Cache do levantamento ANP |
| `RATE_LIMIT_MAX` | `120` | Limite global por janela/IP |
| `RATE_LIMIT_WINDOW_MS` | `300000` | Tamanho da janela do rate limit (5 minutos) |
| `GEOCODE_RATE_LIMIT_MAX` | `30` | Limite de geocodificação |
| `CORS_ORIGINS` | vazio | CORS desligado; informe origens confiáveis se necessário |
| `TRUST_PROXY` | `false` | Ative apenas atrás de proxy reverso confiável |
| `ENFORCE_HTTPS` | `false` | Em produção, rejeita HTTP |
| `ANP_REVENDAS_XLSX_URL` / `ANP_RESUMO_XLSX_URL` | vazio | Opcional: fixa URLs oficiais da ANP |

Não há chave de API para as fontes atuais. O `.env` é ignorado pelo Git; o `.env.example` contém apenas valores fictícios. Em produção, use variáveis de ambiente do provedor e um `HTTP_USER_AGENT` com contato real.

### Produção do backend

- Sirva a API atrás de HTTPS e configure `TRUST_PROXY`/`ENFORCE_HTTPS` de acordo com o proxy.
- Restrinja `CORS_ORIGINS` apenas se houver um cliente web; o app Android não precisa de CORS.
- O rate limit em memória é por instância; ao escalar horizontalmente, use armazenamento compartilhado.
- O cache é perdido ao reiniciar. Para múltiplas instâncias, considere Redis.
- Respeite as políticas de uso do Overpass, Nominatim e dos tiles do OpenStreetMap. Para tráfego elevado, use uma instância/provedor apropriado em vez dos serviços públicos.

## Como executar o Android

### Android Studio

1. Abra **File > Open** e selecione a pasta raiz do projeto (a que contém `settings.gradle.kts`).
2. Aguarde a sincronização do Gradle.
3. Confirme em **SDK Manager** que o SDK 35 e um emulador API 35+ estão instalados.
4. Inicie o backend local.
5. Selecione a configuração `app` e escolha um emulador.
6. Execute. O build de debug usa `http://10.0.2.2:3333/` por padrão, que é o alias do emulador para o `localhost` da máquina.

Para usar outra URL no build de debug, passe a propriedade Gradle:

```bash
./gradlew :app:installDebug -Pfuelcompare.apiBaseUrl=https://api.exemplo.com/
```

No Windows, use `gradlew.bat` no lugar de `./gradlew`. A URL deve terminar com `/`.

O arquivo [gradle.properties](gradle.properties) define o valor padrão do emulador. Evite fixar uma URL de produção em um repositório compartilhado; prefira passá-la no build ou em um arquivo local não versionado. O `local.properties`, gerado pelo Android Studio com o caminho do SDK, também não deve ser versionado.

### Executar sem autorizar localização

O app foi projetado para funcionar sem localização:

1. No onboarding, toque em **Buscar por cidade ou CEP**.
2. Digite, por exemplo, `São Paulo`, `Campinas` ou `01310-100`.
3. Escolha uma sugestão e confirme a região.
4. A busca é feita pelo backend via Nominatim e os postos são carregados a partir da coordenada da região escolhida.
5. Você também pode negar a permissão e continuar pela busca manual.

A localização é usada apenas quando você toca em **Usar minha localização**; não há rastreamento em segundo plano, `ACCESS_FINE_LOCATION` nem serviço de localização contínua.

### Emulador e dispositivo físico

- **Emulador:** o host da máquina é `10.0.2.2`. O build de debug permite HTTP somente para `10.0.2.2` e `localhost`.
- **Dispositivo físico:** use uma URL HTTPS acessível pela rede (por exemplo, um túnel HTTPS ou uma API implantada). O build de release só aceita HTTPS; o build de debug também mantém a validação TLS normal.
- **Mapa:** os tiles são carregados pelo osmdroid diretamente do serviço de tiles do OpenStreetMap, com User-Agent próprio e cache local limitado a 100 MB. O aplicativo não usa chave paga.

## Builds

### Debug

```bash
./gradlew :app:assembleDebug
```

APK gerado em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Instalação por ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release

O projeto **não inclui keystore nem configuração de assinatura**. O artefato gerado localmente e pela CI é `app-release-unsigned.apk`, portanto não é distribuível sem assinatura.

Para gerar uma release de teste:

```bash
./gradlew :app:assembleRelease -Pfuelcompare.apiBaseUrl=https://api.seudominio.com.br/
```

Regras de URL de release em [app/build.gradle.kts](app/build.gradle.kts):

- sem uma propriedade HTTPS válida, o build usa `https://api.fuelcompare.invalid/`, um domínio reservado que falha de forma amigável;
- HTTP não é aceito no release;
- a URL configurada é embutida como `BuildConfig.API_BASE_URL`.

Para preparar uma versão distribuível:

1. Escolha um domínio HTTPS real e mantenha o backend com certificado válido.
2. Gere um keystore de upload fora do repositório.
3. Configure a assinatura no Android Studio (**Build > Generate Signed App Bundle or APK**) ou em uma configuração Gradle local.
4. Nunca versione `.jks`, `.keystore`, `keystore.properties` ou senhas. Esses arquivos já estão no [.gitignore](.gitignore).
5. Valide a API de produção e faça a conferência manual dos fluxos antes de publicar.

O `isMinifyEnabled` está desativado nesta versão. Se o R8 for habilitado no futuro, revise as regras do Room, Hilt, Kotlin Serialization, Retrofit e os modelos usados em reflexão/serialização e teste o APK final.

## Como funcionam os preços e as fontes

### OpenStreetMap / Overpass

Os postos, nomes, marcas, endereços e coordenadas vêm do OpenStreetMap por consultas `amenity=fuel` no Overpass API. O OSM é colaborativo: um posto pode estar incompleto ou desatualizado. Dados © colaboradores do OpenStreetMap, licença ODbL.

### ANP

Os preços individuais vêm do **Levantamento de Preços de Combustíveis (LPC)** semanal da ANP, em arquivo XLSX oficial. As médias municipais vêm do resumo semanal oficial. O backend descobre os arquivos mais recentes na página da ANP e mantém cache; também é possível fixar URLs por ambiente.

A base usada na validação de 24/10/2026 correspondia à semana de **11 a 17/10/2026**, com **4.588 estabelecimentos** com preços individuais e **2.071 médias municipais** carregadas. Esses números descrevem aquela execução e podem mudar a cada semana. Eles não representam preços em tempo real.

A ANP também mantém a API de revendedores (`https://revendedoresapi.anp.gov.br`). Ela foi investigada e expõe dados cadastrais de combustíveis e GLP, **sem preços e sem coordenadas**; por isso não é usada como fonte de preço neste projeto.

### Associação entre OSM e ANP

A associação é deliberadamente conservadora e ocorre em ordem:

1. CNPJ presente em tags do OSM;
2. endereço normalizado único: logradouro + número + município + UF;
3. nome distintivo único + município + UF, recusando nomes que são apenas a bandeira.

Se não houver correspondência unívoca, o posto fica com `prices: []`. Isso significa que muitos postos visíveis no mapa podem não ter preço individual, mesmo que exista uma média municipal para a cidade. Médias municipais nunca são promovidas a preços individuais.

### Regras no aplicativo

- Preço plausível: BRL, valor finito maior que zero, até R$ 100,00 por unidade e unidade compatível com o combustível.
- Preço atual: referência dentro de uma janela de 31 dias; fora dela, o valor é marcado como desatualizado e não concorre ao destaque de menor preço.
- Menor preço: apenas preços individuais elegíveis; desempate por distância, nome e identificador.
- Ordenação por preço: postos sem preço ficam ao final; a ausência não vira zero.
- Comparação: a diferença só é calculada quando os dois postos têm preço individual para o mesmo combustível; caso contrário, a linha informa que só um lado tem preço.
- Cache: a comparação offline usa a data do snapshot **mais antigo** dos dois postos, para não superestimar a atualidade dos dados.

### Cache do backend

O backend mantém cache TTL em memória para buscas próximas, detalhes e levantamento ANP. Quando um provedor falha e existe uma resposta anterior válida, a API pode devolvê-la com aviso e data; quando não há cache, devolve erro controlado. O cache não é um banco de dados e é perdido ao reiniciar o processo.

## Testes e verificação

### Comandos

Android, na raiz:

```bash
./gradlew :core:network:test :domain:test :data:testDebugUnitTest
./gradlew :app:assembleDebug :app:assembleRelease
./gradlew :app:lintDebug
# Requer emulador/dispositivo conectado:
./gradlew :app:connectedDebugAndroidTest
```

Backend:

```bash
cd fuelcompare-backend
npm test
npm run typecheck
npm run build
npm run audit
npm audit
```

### Resultados da última execução (24/10/2026)

| Verificação | Resultado |
| --- | --- |
| Testes unitários Android | **63 aprovados**, 0 falhas, 0 ignorados |
| Testes instrumentados Compose | **3 aprovados** em emulador Pixel_4, API 37 |
| Build debug | Sucesso; `app-debug.apk` |
| Build release | Sucesso; `app-release-unsigned.apk` |
| Lint Android | **0 erros, 70 avisos** (principalmente versões novas de dependências; um aviso de qualificador `v26` nos ícones adaptativos) |
| Backend | **9 arquivos, 85 testes aprovados** |
| TypeScript | `typecheck` e `build` sem erros |
| Auditoria npm | **0 vulnerabilidades** em produção e no total |
| Testes manuais | Onboarding, busca manual, Home, detalhes, comparação manual, cache offline, configurações e tema escuro verificados no emulador |

Os 63 testes unitários se dividem em: 37 no `domain` (regras de preço, menor preço, ordenação, comparação e casos de uso), 23 no `data` (mapeadores, falhas de rede e repositório) e 3 no `core:network` (DTOs). Os 3 testes instrumentados cobrem os componentes compartilhados de estado offline, preço e banner informativo.

A comparação automática dos dois postos mais baratos depende de haver dois preços individuais recentes. Na amostra de São Paulo usada na validação manual, apenas um posto tinha preço individual associado; por isso o fluxo automático não foi exercitado em tela nessa região, embora a regra de elegibilidade e a comparação sejam cobertas por testes unitários. A comparação manual foi validada em tela e informou corretamente quando não havia combustíveis comparáveis.

A CI em [.github/workflows/ci.yml](.github/workflows/ci.yml) executa:

- backend: instalação, typecheck, 85 testes, build e `npm audit --omit=dev --audit-level=high`;
- Android: 63 testes unitários, builds debug/release e lint.

**Os testes instrumentados Compose não são executados pela CI atual**, porque exigem um emulador; eles foram executados localmente conforme registrado acima. Nenhum resultado deste README foi inferido: builds e testes que não foram executados estão marcados como tal.

### Dependabot

[.github/dependabot.yml](.github/dependabot.yml) verifica semanalmente dependências do Gradle, npm e GitHub Actions, com PRs separados por ecossistema. A auditoria npm também roda na CI. O `overrides` do backend fixa `uuid ^11.1.1` para eliminar uma versão transitiva vulnerável trazida pelo ExcelJS; Zod e Vitest foram atualizados para versões corrigidas.

## Segurança e privacidade

### Aplicativo

- Manifesto solicita apenas `INTERNET`, `ACCESS_NETWORK_STATE` e `ACCESS_COARSE_LOCATION`.
- Não há localização em segundo plano, serviço de localização contínuo nem `ACCESS_FINE_LOCATION`.
- Nenhuma chave secreta no APK ou no código-fonte.
- Build de debug permite HTTP somente para `10.0.2.2` e `localhost`; todos os outros domínios continuam exigindo HTTPS.
- Build de release exige HTTPS e usa os certificados do sistema; a validação TLS não é desativada.
- Backup desativado (`allowBackup=false`) e regras de extração de dados que excluem bancos, preferências e arquivos.
- Logs não incluem tokens, credenciais ou dados sensíveis; os erros exibidos são mensagens amigáveis.
- Sem autenticação, cadastro, anúncios, analytics ou rastreamento.
- Permissão de localização solicitada apenas por ação do usuário; o app continua útil por busca manual.

### Backend

- Helmet, CSP, `nosniff`, `no-referrer` e HSTS em produção.
- CORS desligado por padrão; quando configurado, aceita apenas origens explícitas.
- JSON limitado a 10 kB.
- Rate limiting global e um limite menor para geocodificação.
- Validação Zod estrita de parâmetros, com rejeição de campos desconhecidos e limites de raio, resultados e IDs.
- Download externo limitado por tamanho; timeouts e tentativas controladas em Overpass, Nominatim e ANP.
- Erros públicos padronizados, sem stack trace, caminhos internos, SQL ou segredos; o detalhe técnico fica no log privado.
- Logs com `requestId`, método e rota, sem query string; redação de `authorization` e `cookie`.
- `.env` ignorado; `.env.example` somente com valores fictícios.
- Encerramento gracioso e limites de corpo/resposta.

## Limitações conhecidas

- **Não há preços em tempo real.** A ANP publica um levantamento semanal de uma amostra de estabelecimentos; a data de referência é sempre exibida.
- **Cobertura de associação limitada.** A correspondência conservadora entre OSM e ANP deixa muitos postos sem preço individual. Isso é uma escolha de integridade, não um valor ausente a ser preenchido.
- **Média municipal não é preço do posto.** Ela aparece separada e nunca entra no destaque de menor preço ou no cálculo de economia de um posto.
- **Overpass é instável.** A disponibilidade varia e a primeira busca pode levar dezenas de segundos; a API pode responder 502/504 controlados. O app usa cache quando existe e mostra erro amigável quando não existe.
- **Timeout do app.** O cliente usa 30 segundos de leitura; uma resposta inicial mais lenta que isso pode cair no cache ou em erro, conforme o caso.
- **Cache do backend em memória.** Não há persistência entre reinícios nem compartilhamento entre instâncias.
- **Geocodificação pública limitada.** Nominatim exige uso justo; o backend serializa chamadas e não geocodifica postos em massa.
- **Mapa com tiles públicos do OSM.** Adequado para desenvolvimento e uso moderado; produção de alto volume deve usar um provedor de tiles apropriado ou infraestrutura própria, respeitando as políticas de uso.
- **Sem contribuição comunitária.** O modelo já prevê `priceType`, origem e validação, mas não há conta de usuário, moderação ou envio de preço.
- **Sem autenticação/autorização.** Não é necessária para as fontes e o escopo atuais; a API deve ser protegida por limite de taxa e implantação HTTPS.
- **Release não assinada e minificação desativada.** O artefato de release gerado é somente para validação; é preciso configurar assinatura e testar R8 antes de distribuir.
- **Testes instrumentados limitados a componentes.** Não há suíte E2E completa; os fluxos principais foram validados manualmente no emulador.

## Próximos passos

1. Deploy HTTPS do backend com cache distribuído (Redis), métricas e alertas de disponibilidade.
2. Ampliar a cobertura de associação OSM↔ANP com revisão administrativa de correspondências duvidosas, sem relaxar a regra de unicidade.
3. Contribuição comunitária de preços com moderação, limite por usuário, detecção de abuso e histórico.
4. Suíte de testes instrumentados de fluxo completo (onboarding, busca manual, detalhes, comparação e offline).
5. Empacotar a variante de release com assinatura segura, R8 e políticas de privacidade finais.
6. Avaliar provedor de tiles com SLA e política comercial para uso em produção.
7. Internacionalização opcional e acessibilidade contínua; hoje a interface é pt-BR, BRL e unidades métricas.

## Créditos e atribuições

- **OpenStreetMap**: dados de postos, endereços e mapa © colaboradores do OpenStreetMap, disponibilizados sob a [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/). O aplicativo usa osmdroid com os tiles do OpenStreetMap e exibe a atribuição no mapa e na interface.
- **Nominatim**: geocodificação baseada em dados do OpenStreetMap; uso sujeito à [política do Nominatim](https://operations.osmfoundation.org/policies/nominatim/).
- **ANP**: preços e médias municipais do Levantamento de Preços de Combustíveis, dados públicos oficiais do Governo Federal. Links e termos no [portal da ANP](https://www.gov.br/anp/).
- **Overpass API**: consultas de postos; uso sujeito à [política do Overpass](https://wiki.openstreetmap.org/wiki/Overpass_API#Usage_policy).
- **osmdroid**: biblioteca de mapa Android de código aberto.
- **AndroidX, Kotlin, Jetpack Compose, Hilt, Room, Retrofit, OkHttp, Kotlin Serialization, Coil**: projetos e bibliotecas de seus respectivos mantenedores.
- **Express, Zod, Helmet, ExcelJS, Pino, Vitest**: bibliotecas do ecossistema Node.js de seus respectivos mantenedores.

O código deste repositório foi produzido para fins educacionais e de portfólio. Os dados mantêm as licenças e os termos de suas fontes; marcas e nomes de postos pertencem aos seus titulares. Não há vínculo com a ANP, OpenStreetMap, Nominatim ou qualquer rede de postos.

---

Última verificação documentada: **24/10/2026**. Preços, arquivos semanais e disponibilidade de provedores externos mudam com o tempo; consulte sempre a data exibida no aplicativo e a fonte oficial.
