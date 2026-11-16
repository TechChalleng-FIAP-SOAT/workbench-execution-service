# workbench-execution-service — Execution Service

Microsserviço de **estoque, produtos e execução** da plataforma Workbench (Tech
Challenge — FIAP Pós Tech, Fase 4). Dono do estoque e do ciclo
**reserva → baixa → retorno**. É o **único serviço NoSQL** do conjunto (MongoDB).

> **Contexto (Fase 4):** na Fase 3 a oficina era um **monólito** (`workbench-monolith`,
> mantido como base histórica). Na Fase 4 a aplicação foi refatorada em
> **microsserviços independentes**, cada um com seu repositório, infraestrutura e
> banco próprios. Este repositório é o **Execution Service**. A divisão dos serviços e
> a estratégia da Saga estão nos ADRs (`workbench-monolith/docs/adr` — ADR-0006 e ADR-0005).

---

## Sumário

- [Responsabilidades](#responsabilidades)
- [Requisitos obrigatórios do PDF cobertos aqui](#requisitos-obrigatórios-do-pdf-cobertos-aqui)
- [Banco NoSQL (MongoDB) e reserva atômica](#banco-nosql-mongodb-e-reserva-atômica)
- [Comunicação e participação na Saga](#comunicação-e-participação-na-saga)
- [Arquitetura da aplicação (hexagonal)](#arquitetura-da-aplicação-hexagonal)
- [Arquitetura de infraestrutura](#arquitetura-de-infraestrutura)
- [Fluxo de Deploy (CI/CD)](#fluxo-de-deploy-cicd)
- [Tecnologias](#tecnologias)
- [Como executar localmente](#como-executar-localmente)
- [Deploy em Kubernetes / AWS](#deploy-em-kubernetes--aws)
- [Referência de endpoints](#referência-de-endpoints)
- [Testes e cobertura](#testes-e-cobertura)
- [Documentação da API (Swagger)](#documentação-da-api-swagger)

---

## Responsabilidades

- CRUD de **produtos** (peças/insumos) e de **estoque**
- Entrada/saída de estoque, cada uma registrada como **movimentação** (trilha de auditoria
  gravada no banco; não há rota de consulta)
- **Consulta de disponibilidade** (para montar o orçamento): informa a falta com nome
  da peça e quantidade disponível
- **Reserva de estoque** com máquina de estados, orquestrada pela Saga:
  `ACTIVE` (reservado) → `CONFIRMED` (baixa, na aprovação) **ou** `ACTIVE` → `RELEASED`
  (retorno, na recusa/falha). Só uma reserva ACTIVE muda de status.

---

## Requisitos obrigatórios do PDF cobertos aqui

| Requisito (PDF Fase 4) | Como este serviço atende |
|---|---|
| ≥3 microsserviços independentes | É um dos 4 serviços, com repo/infra/banco próprios |
| Banco próprio | **MongoDB** exclusivo; nenhum outro serviço o acessa |
| **SQL e NoSQL (no conjunto)** | **Cumpre o requisito NoSQL** da plataforma (MongoDB); os demais são PostgreSQL |
| Nenhum acesso ao banco de outro serviço | Expõe disponibilidade via **REST**; reserva/baixa/liberação via **mensageria** |
| REST + mensageria assíncrona | `POST /api/stocks/availability` (síncrono) + **SNS/SQS** (comandos/eventos) |
| Saga Pattern + compensação | Participa da Saga: reserva/baixa/liberação são os passos compensáveis |
| Testes + 80% cobertura + Sonar | JaCoCo gate 80% no `verify` + SonarCloud (cobertura ~89%) |
| CI/CD + deploy K8s + branch protegida | Pipeline próprio; `main` protegida |

---

## Banco NoSQL (MongoDB) e reserva atômica

Único serviço **NoSQL** da plataforma — é ele que satisfaz o requisito obrigatório
"ao menos um banco não relacional". A reserva (ajuste de estoque + registro da reserva
+ movimento) é **atômica** via **transações multi-documento** do MongoDB
(`@Transactional` + o bean `MongoTransactionManager` em `MongoConfig` — sem ele o Spring
ignora as anotações), o que exige um **replica set** (o ambiente local sobe
`mongod --replSet rs0`; o Atlas e o Testcontainers já são replica set).

Duas gravações concorrentes no mesmo estoque (ex.: duas réplicas reservando o mesmo
produto) fazem o MongoDB abortar a segunda (*WriteConflict*) em vez de perder uma
atualização; esse erro técnico volta para a fila e é tentado de novo — só falta de estoque
vira `ReservaFalhou`. As escritas de estoque pela API (entrada, saída, mínimo) também são
transacionais.

Quantidades e preços são gravados como **`Decimal128`** (o decimal exato do MongoDB,
`spring.data.mongodb.representation.big-decimal=decimal128`). Por isso "estoque baixo" é uma
consulta no próprio banco (`$expr` com `$lte`), e não um filtro em memória: como texto,
`"10" < "9"`. Documentos antigos, gravados como texto, são convertidos no start pelo
`DecimalStorageMigration` (idempotente; teste em `DecimalStorageIntegrationTest`).

> Decisão (ADR-0005): **Mongo puro** neste serviço (um serviço = um banco), em vez de
> Postgres+Mongo, evitando dois datasources. A atomicidade da reserva é garantida pela
> transação do Mongo.

**Por que MongoDB aqui:** o PDF exige ao menos um banco NoSQL, e este é o serviço que mais
se encaixa: produtos (peças e insumos com atributos diferentes), estoque, reservas e
movimentos são documentos lidos e gravados por produto ou por OS, sem junção com dados de
outro serviço. **Por que Atlas:** o M0 é gerenciado e gratuito; o DocumentDB não tem plano
gratuito e um Mongo no cluster exigiria volume, backup e replica set operados por nós.
Todas as decisões, em perguntas: `workbench-technical-documentation/01 - Arquitetura/Decisoes de Arquitetura.md`.

---

## Comunicação e participação na Saga

| Caso | Mecanismo |
|---|---|
| Consulta de disponibilidade (montar orçamento) | **REST síncrono** (`POST /api/stocks/availability`) |
| Reserva / baixa / liberação (passos da Saga) | **Mensageria SNS/SQS** |

| Recebe (comando) | Faz | Publica (evento) |
|---|---|---|
| `ComandoReservarEstoque` | reserva `ACTIVE` | `ReservaConfirmada` (peças precificadas) ou `ReservaFalhou` |
| `ComandoConfirmarReserva` | baixa de estoque | `ReservaBaixada` |
| `ComandoLiberarReserva` | devolve ao estoque | `ReservaLiberada` |

No `ReservaConfirmada`, o Execution (dono do catálogo) enriquece cada peça com `name`,
`sku`, `brand` (marca) e `unitPrice`. A **mesma peça em marcas diferentes** é um
produto distinto, com preço distinto.

Comandos repetidos (reenvio por timeout ou SQS) não duplicam efeito: se a OS já tem
reserva ACTIVE, a reserva existente é devolvida; confirmar/liberar de novo devolve o
resultado anterior. Consumer com dedupe por `messageId` e redrive para **DLQ**. Nomes e
campos seguem o contrato executável `workbench-messaging/contracts/saga-messages.json`
(`ExecutionSagaContractTest`).

---

## Arquitetura da aplicação (hexagonal)

```
src/main/java/com/workbench/workshop/
├── adapter/
│   ├── in/web/            # ProductController, StockController + ProductDtos/StockDtos
│   └── out/persistence/   # Adapters MongoDB (product, stock, reservation, movement)
├── application/           # Uma classe por caso de uso + ports
│   ├── product/           # Create/FindById/List/DeleteProduct
│   └── stock/             # CreateStock, FindStockByProduct, ListStock, ListLowStock,
│                          # RegisterStockEntry/Exit, UpdateMinimumStock, CheckAvailability,
│                          # ReserveStock, Confirm/ReleaseReservation, FindReservationsByServiceOrder
├── domain/                # Product, Stock (sem setters: só reserve/confirm/release/…), StockReservation, StockMovement
└── infrastructure/        # config (Mongo + MongoTransactionManager), messaging (SNS/SQS)
```

---

## Arquitetura de infraestrutura

Banco **MongoDB próprio e gerenciado** — **MongoDB Atlas** (cluster free tier `M0`),
fora do cluster. Os pods do `execution-service` (namespace `execution-service` no EKS
`workbench-platform-<env>`) conectam no Atlas pela connection string `mongodb+srv://...`,
injetada no `MONGODB_URI` via **Secret** (`k8s/secret.yaml`). Sem acesso cruzado a
bancos. Tópicos/filas no repo `workbench-messaging`.

> **Por ambiente:**
> - **Produção/AWS:** MongoDB Atlas (gerenciado). Nenhum Mongo roda no cluster.
> - **Local:** o Mongo sobe pelo `workbench-messaging/docker-compose.dev.yml` (porta 27017).
> - **Opcional (k8s sem Atlas):** `kubectl apply -f k8s/mongo-local.yaml` sobe um Mongo
>   in-cluster — **não** é aplicado pelo pipeline.

---

## Fluxo de Deploy (CI/CD)

```
push (main/develop) ou PR
   │
   ▼
unit-test ──▶ code-analysis ──▶ build ──▶ docker-build-push ──▶ deploy-aws
(mvn verify   (JaCoCo gate 80%  (mvn      (imagem p/ Docker Hub  (EKS, só se
 + JaCoCo      + Sonar)          package)  usuario/execution-service) USE_AWS=true)
 badge)
```

Secrets/variáveis comuns aos serviços (`DOCKER_*`, `SONAR_TOKEN`, `AWS_*`, `USE_AWS`) —
ver o README do `workbench-os-service` —, mais `JWT_PUBLIC_KEY` e o **`MONGODB_URI`**
(connection string do Atlas). O job `deploy-aws` monta o Secret do pod a partir deles (o
`k8s/secret.yaml` só tem placeholders) e falha no início se faltar algum.
Um **cluster MongoDB Atlas** (free tier `M0`) é pré-requisito do deploy AWS: crie o
cluster, um usuário de banco, libere o acesso de rede e use a URI `mongodb+srv://...`
como `MONGODB_URI`.

---

## Tecnologias

| Tecnologia | Finalidade |
|---|---|
| Java 21 + Spring Boot 4.0.9 | Framework web e DI |
| MongoDB 7 — Atlas (prod) / replica set (local) | Banco próprio gerenciado (**NoSQL**); transação multi-documento |
| AWS SNS/SQS (SDK v2) | Mensageria da Saga + DLQ |
| Micrometer + Datadog | Métricas e observabilidade |
| SpringDoc OpenAPI | Swagger UI |
| JaCoCo (gate 80%) + SonarCloud | Qualidade e cobertura |
| Testcontainers (MongoDB) | Integração com MongoDB real (replica set) |

---

## Como executar localmente

Requer um MongoDB em **replica set** (para as transações). Exemplo com Docker:

```bash
docker run -d -p 27017:27017 mongo:7 --replSet rs0
# em outro terminal, inicie o replica set:
docker exec -it <container> mongosh --eval "rs.initiate()"

./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

> **Sem valores padrão para segredos.** Fora do perfil `local` (`JWT_PUBLIC_KEY`) são
> obrigatórios: sem eles o serviço não sobe. O perfil `local` (`application-local.yml`)
> traz os valores de desenvolvimento e é ativado pelo compose do `workbench-messaging`.

| Variável | Default | Descrição |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/execution` | Banco próprio (precisa ser replica set) |
| `JWT_PUBLIC_KEY` | — (obrigatório; dev no perfil `local`) | Chave **pública** RS256 da Lambda de login: o serviço verifica token, não emite |
| `MESSAGING_ENABLED` | `false` | liga SNS/SQS; `false` roda sem broker |
| `AWS_ENDPOINT` | — | vazio = AWS real; `http://localhost:4566` = LocalStack |
| `EXECUTION_COMMANDS_QUEUE_URL` | — | Fila de comandos da Saga para o Execution |
| `EXECUTION_EVENTS_TOPIC_ARN` | — | Tópico onde o Execution publica as respostas |
| `SCHEDULING_POOL_SIZE` | `4` | Threads dos jobs `@Scheduled` |

---

## Deploy em Kubernetes / AWS

Manifests em `k8s/`. Deploy automatizado pelo pipeline (`deploy-aws`, quando
`USE_AWS=true`) no cluster `workbench-platform-<branch>`, namespace `execution-service`.

O que os manifests garantem:
- **Rede:** o Service é um **NLB interno** (`aws-load-balancer-internal`), alcançável só de dentro da VPC. O tráfego de fora entra pelo API Gateway (VPC Link + authorizer JWT); Swagger e actuator não ficam na internet. Se o Service já existia como LB público, o pipeline o recria uma vez (o esquema de um LB não muda depois de criado). Uma `NetworkPolicy` deixa entrar só a porta 8080 (vale com NetworkPolicy ligada no VPC CNI).
- **Imagem:** o pipeline troca o `REPLACE_IMAGE` do `app.yaml` pela tag do commit antes do `kubectl apply` (um rollout por deploy). Não há tag `:latest`; o `dd-java-agent` injetado pelo Datadog tem versão fixa (`v1.68.2`).
- **Pods:** usuário não-root (UID 10001), sem escalar privilégio, sem capabilities, sistema de arquivos somente leitura (só `/tmp` gravável), `seccomp` padrão. A subida tem um `startupProbe` (até 5 min); liveness e readiness começam depois. A JVM usa até 75% da memória do container (`-XX:MaxRAMPercentage=75.0`).
- **Escala:** o número de réplicas é só do HPA (o `Deployment` não fixa `replicas`), e um `PodDisruptionBudget` tira no máximo 1 pod por vez numa troca de nó.
- **Banco:** MongoDB Atlas (`MONGODB_URI` no Secret). `k8s/mongo-local.yaml` é só para testar num cluster sem Atlas (o pipeline não aplica).

---

## Segurança (JWT)

Defesa em profundidade: o serviço **valida o JWT por conta própria** (Spring Security +
filtro RS256 com a chave pública da Lambda). Catálogo e estoque são operações internas
da oficina, então **todas as rotas exigem token de staff (ADMIN/TECHNICIAN)**; públicas
apenas `GET /actuator/health*` e Swagger/OpenAPI.

### Boas práticas OWASP aplicadas
- **A01/A07:** todas as rotas exigem staff; JWT validado pelo serviço.
- **A03 (validação):** `@Valid` + Bean Validation nos DTOs de produto/estoque; acesso a
  dados via Spring Data MongoDB (sem interpolação de query).
- **A05:** headers `X-Frame-Options: DENY` + `nosniff`; `k8s/secret.yaml` com placeholders
  (`REPLACE_ME`) — os valores reais vêm dos secrets da org, montados pela pipeline. A API
  responde com DTOs (`ProductResponse`…), nunca com a entidade de domínio.
- **A09:** subject do token mascarado no log (MDC).

> Hotspot `java:S4502` (CSRF disabled) marcado **Safe**: API REST stateless com JWT.

---

## Referência de endpoints

Todas exigem token de staff (ADMIN/TECHNICIAN).

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/products` | Adicionar peça/insumo |
| GET | `/api/products` | Listar o catálogo |
| GET | `/api/products/{id}` | Buscar produto por ID |
| PUT | `/api/products/{id}` | Atualizar dados da peça/insumo (SKU continua único → 409) |
| DELETE | `/api/products/{id}` | Remover peça/insumo |
| POST | `/api/stocks` | Criar estoque de um produto |
| GET | `/api/stocks` | Listar estoques |
| GET | `/api/stocks/low` | Listar estoques no mínimo ou abaixo dele |
| GET | `/api/stocks/product/{id}` | Buscar estoque por produto |
| PATCH | `/api/stocks/product/{id}/entry` | Entrada de estoque (**adicionar quantidade** a produto existente) |
| PATCH | `/api/stocks/product/{id}/exit` | Saída de estoque |
| PATCH | `/api/stocks/product/{id}/minimum` | Atualizar a quantidade mínima |
| POST | `/api/stocks/availability` | Verificar disponibilidade de itens |
| POST | `/api/stocks/reservations` | Reservar estoque |
| GET | `/api/stocks/reservations/service-order/{osId}` | Listar reservas de uma OS |
| PATCH | `/api/stocks/reservations/service-order/{osId}/confirm` | Confirmar (baixa) |
| PATCH | `/api/stocks/reservations/service-order/{osId}/release` | Liberar (retorno) |

> Na Saga, reservar/confirmar/liberar chegam por **mensageria**; as rotas REST de reserva
> existem para operação manual e testes.

---

## Testes e cobertura

```bash
./mvnw verify
```

- **Gate JaCoCo 80%** no `verify`.
- Integração com **Testcontainers MongoDB** (replica set) — fluxo real reserva→baixa.
- **Teste de contrato da Saga** (`ExecutionSagaContractTest`): consome o exemplo de
  `ComandoReservarEstoque` do contrato e confere os campos de tudo o que publica.
- Cobertura atual (medida em 2026-11-16): **89,3% de linhas** e 77,4% de branches (`target/site/jacoco/index.html`).
- Evidência por execução do CI: artefato `jacoco-report` do job `unit-test` (relatório HTML) e o Quality Gate do SonarCloud no job `code-analysis`.

---

## Documentação da API (Swagger)

| Recurso | URL (local) |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
