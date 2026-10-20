# workbench-execution-service — Execution Service (Tech Challenge Fase 4)

Microsserviço responsável por **estoque, produtos e execução** da plataforma
Workbench. Dono do estoque e do ciclo de **reserva → baixa → retorno** (ADR-0005).

Extraído dos módulos `stock` e `product` do monólito, adaptado para **MongoDB**
(único serviço NoSQL do conjunto — ADR-0003/0005), em Arquitetura Hexagonal.

## Responsabilidades

- CRUD de produtos (peças/insumos) e de estoque
- Entrada/saída de estoque com histórico de movimentações
- **Consulta de disponibilidade** (para montar orçamento): informa falta de peça
  com nome e quantidade disponível
- **Reserva de estoque** com máquina de estados, orquestrada pela Saga:
  `ACTIVE` (reservado) → `CONFIRMED` (baixa, na aprovação) → `RELEASED` (volta, na recusa)

## Banco: MongoDB (NoSQL)

Único serviço NoSQL da plataforma. A reserva (ajuste de estoque + registro +
movimento) é **atômica** via transações multi-documento do Mongo (`@Transactional`),
o que exige um **replica set** (o manifesto local sobe `mongod --replSet rs0`; o
Testcontainers já provê replica set de 1 nó).

## Comunicação

| Caso | Mecanismo |
|---|---|
| Consulta de disponibilidade (montar orçamento) | **REST síncrono** (`POST /api/stocks/availability`) |
| Reserva / baixa / liberação (passos da Saga) | **Mensageria SNS/SQS** |

### Comandos consumidos / eventos publicados (Saga)

| Recebe (comando) | Faz | Publica (evento) |
|---|---|---|
| `ComandoReservarEstoque` | reserva ACTIVE | `ReservaConfirmada` (com peças precificadas) ou `ReservaFalhou` (motivo) |
| `ComandoConfirmarReserva` | baixa de estoque | `ReservaBaixada` |
| `ComandoLiberarReserva` | devolve ao estoque | `ReservaLiberada` |

No `ReservaConfirmada`, o Execution (dono do catálogo) enriquece cada peça com
`name`, `sku`, `brand` (marca) e `unitPrice` (preço de venda) — é esse snapshot
que o OS Service repassa ao Billing para montar o orçamento, sem o Billing acessar
o estoque. A mesma peça em marcas diferentes é um produto distinto (preço distinto).

Consumer com dedupe por `messageId` e redrive para DLQ em caso de erro.
`messaging.enabled=false` (default) permite rodar sem broker (dev/testes).

## Endpoints REST principais

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/products` | Adicionar peça/insumo |
| DELETE | `/api/products/{id}` | Remover peça/insumo |
| POST | `/api/stocks` | Criar estoque de um produto |
| PATCH | `/api/stocks/product/{id}/entry` | Entrada |
| PATCH | `/api/stocks/product/{id}/exit` | Saída |
| POST | `/api/stocks/availability` | Verificar disponibilidade de itens |
| POST | `/api/stocks/reservations` | Reservar |
| PATCH | `/api/stocks/reservations/service-order/{osId}/confirm` | Confirmar (baixa) |
| PATCH | `/api/stocks/reservations/service-order/{osId}/release` | Liberar (retorno) |

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## Rodar localmente

```bash
# requer um MongoDB em replica set (para transações). Ex. via Docker:
# docker run -d -p 27017:27017 mongo:7 --replSet rs0  (e rs.initiate())
mvn spring-boot:run
```

## Qualidade

- Cobertura **86.5%** (JaCoCo, gate de 80%); testes unitários + integração com
  **Testcontainers MongoDB** (fluxo real reserva→baixa validado).
- Pipeline: `unit-test → code-analysis (JaCoCo+Sonar) → build → docker → deploy EKS`.

## Status na Fase 4

- [x] Extração hexagonal de stock + product para MongoDB
- [x] Regra reserva → baixa → retorno (domínio + transação Mongo)
- [x] API REST (CRUD estoque/produto + disponibilidade)
- [x] Consumer/publisher da Saga (SNS/SQS); `ReservaConfirmada` com peças precificadas (marca/preço)
- [x] Participa da Saga orquestrada pelo OS Service (reserva/baixa/liberação)
- [ ] Validação ponta a ponta no ambiente integrado (homologação — manual)
