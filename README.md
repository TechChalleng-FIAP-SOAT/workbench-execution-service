# workbench-execution-service — Execution Service

Microsserviço de **estoque, produtos e execução** da plataforma Workbench (Tech
Challenge — FIAP Pós Tech, Fase 4). Dono do estoque e do ciclo
**reserva → baixa → retorno**. É o **único serviço NoSQL** do conjunto (MongoDB).

> **Contexto (Fase 4):** na Fase 3 a oficina era um **monólito** (`workbench-monolith`,
> mantido como base histórica). Na Fase 4 a aplicação foi refatorada em
> **microsserviços independentes**, cada um com seu repositório, infraestrutura e
> banco próprios. Este repositório é o **Execution Service**. A divisão dos serviços e
> a estratégia da Saga estão nos ADRs (`workbench-monolith/docs/adr` — ADR-0003 e ADR-0005).

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
- Entrada/saída de estoque com **histórico de movimentações**
- **Consulta de disponibilidade** (para montar o orçamento): informa a falta com nome
  da peça e quantidade disponível
- **Reserva de estoque** com máquina de estados, orquestrada pela Saga:
  `ACTIVE` (reservado) → `CONFIRMED` (baixa, na aprovação) → `RELEASED` (retorno, na recusa)

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
(`@Transactional`), o que exige um **replica set** (o ambiente local sobe
`mongod --replSet rs0`; o Testcontainers provê um replica set de 1 nó).

> Decisão (ADR-0005): **Mongo puro** neste serviço (um serviço = um banco), em vez de
> Postgres+Mongo, evitando dois datasources. A atomicidade da reserva é garantida pela
> transação do Mongo.

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
produto distinto, com preço distinto. Consumer com dedupe por `messageId` e redrive
para **DLQ**.

---

## Arquitetura da aplicação (hexagonal)

```
src/main/java/com/workbench/workshop/
├── adapter/
│   ├── in/web/            # ProductController, StockController
│   └── out/persistence/   # Adapters MongoDB (product, stock, reservation, movement)
├── application/           # Use cases + ports (ReservationUseCases, ProductUseCases, CheckAvailability)
├── domain/                # Product, Stock, StockReservation (máquina de estados), StockMovement
└── infrastructure/        # config (Mongo), messaging (SNS/SQS)
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

Secrets/variáveis idênticos aos demais serviços (`DOCKER_*`, `SONAR_TOKEN`, `AWS_*`,
`USE_AWS`), mais o **`MONGODB_URI`** (connection string do Atlas) no Secret do serviço.
Um **cluster MongoDB Atlas** (free tier `M0`) é pré-requisito do deploy AWS: crie o
cluster, um usuário de banco, libere o acesso de rede e use a URI `mongodb+srv://...`
como `MONGODB_URI`.

---

## Tecnologias

| Tecnologia | Finalidade |
|---|---|
| Java 21 + Spring Boot 4.0.5 | Framework web e DI |
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

./mvnw spring-boot:run
```

| Variável | Default | Descrição |
|---|---|---|
| `MESSAGING_ENABLED` | `false` | liga SNS/SQS; `false` roda sem broker |
| `AWS_ENDPOINT` | — | vazio = AWS real; `http://localhost:4566` = LocalStack |

---

## Deploy em Kubernetes / AWS

Manifests em `k8s/`. Deploy automatizado pelo pipeline (`deploy-aws`, quando
`USE_AWS=true`) no cluster `workbench-platform-<branch>`, namespace `execution-service`.

---

## Segurança (JWT)

Defesa em profundidade: o serviço **valida o JWT por conta própria** (Spring Security +
filtro HS256 com o `JWT_SECRET` compartilhado). Catálogo e estoque são operações internas
da oficina, então **todas as rotas exigem token de staff (ADMIN/TECHNICIAN)**; públicas
apenas `GET /actuator/health*` e Swagger/OpenAPI.

### Boas práticas OWASP aplicadas
- **A01/A07:** todas as rotas exigem staff; JWT validado pelo serviço.
- **A03 (validação):** `@Valid` + Bean Validation nos DTOs de produto/estoque; acesso a
  dados via Spring Data MongoDB (sem interpolação de query).
- **A05:** headers `X-Frame-Options: DENY` + `nosniff`; `k8s/secret.yaml` com placeholders
  (`REPLACE_ME`), segredos reais via secret manager.
- **A09:** subject do token mascarado no log (MDC).

> Hotspot `java:S4502` (CSRF disabled) marcado **Safe**: API REST stateless com JWT.

---

## Referência de endpoints

Todas exigem token de staff (ADMIN/TECHNICIAN).

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/products` | Adicionar peça/insumo |
| DELETE | `/api/products/{id}` | Remover peça/insumo |
| POST | `/api/stocks` | Criar estoque de um produto |
| PATCH | `/api/stocks/product/{id}/entry` | Entrada de estoque (**adicionar quantidade** a produto existente) |
| PATCH | `/api/stocks/product/{id}/exit` | Saída de estoque |
| POST | `/api/stocks/availability` | Verificar disponibilidade de itens |
| POST | `/api/stocks/reservations` | Reservar estoque |
| PATCH | `/api/stocks/reservations/service-order/{osId}/confirm` | Confirmar (baixa) |
| PATCH | `/api/stocks/reservations/service-order/{osId}/release` | Liberar (retorno) |

---

## Testes e cobertura

```bash
./mvnw verify
```

- **Gate JaCoCo 80%** no `verify`.
- Integração com **Testcontainers MongoDB** (replica set) — fluxo real reserva→baixa.
- Cobertura atual: **~89%** (`target/site/jacoco/index.html`).

---

## Documentação da API (Swagger)

| Recurso | URL (local) |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
