# FinFast documentation

Use this index to find the authority for a concern. Read only the sources
relevant to the current task.

## Current authorities

| Concern | Authority |
|---|---|
| Product purpose, V1 scope, outcomes, and vocabulary | [Vision](product/vision.md), [product requirements](product/prd.md), and [glossary](product/glossary.md) |
| Detailed approved behavior and canonical IDs | [Use cases](01-discovery/04-use-cases.md), [business rules](01-discovery/06-business-rules.md), [functional specifications](02-requirements/12-functional-specifications.md), and [UX journeys](03-design/14-ux-user-flows-and-e2e.md), until each obligation is accepted into an appropriate successor |
| Target architecture | [Architecture overview](architecture/overview.md) |
| Module ownership, allowed dependencies, and collaboration | [Module boundaries](architecture/boundaries.md); exact Java contracts live in source |
| Financial data and transaction constraints | [Data and transactions](architecture/data-and-transactions.md); [Flyway migrations](../backend/src/main/resources/db/migration/) own the schema |
| External REST contract | [API guidance](03-design/17-api-contracts.md) explains inspection and change policy; [OpenAPI V1](03-design/contracts/openapi-v1.yaml) is the exact contract |
| Development workflow and documentation updates | [Development workflow](development/workflow.md) |
| Verification strategy and benchmark method | [Verification strategy](verification/strategy.md) |
| Release gates and owner signoff | [Release checklist](05-verification/23-uat-and-release-checklist.md) |
| Technical decisions and approval provenance | [Design decision register](6-governance/25-design-decision-register.md), the sole technical decision authority |

## Specialized and retained sources

- The [system architecture source](03-design/15-system-architecture.md) retains
  detailed package, adapter, and enforcement conventions. The
  [detailed data design](03-design/16-domain-model-and-data-design.md) retains
  selected approved transaction, locking, idempotency, and history protocols.
- The [module guide](03-design/module-contracts-and-boundaries.md) is retained
  for existing links. Exact declarations are in the
  [Java source tree](../backend/src/main/java/com/finfast/); the Markdown guide
  does not reproduce signatures.
- [Shared error and money guidance](03-design/shared-error-money-contracts.md)
  and [logging/error/tracing guidance](03-design/logging-and-error-handling.md)
  cover specialized cross-cutting engineering concerns.
- The [migration README](../backend/src/main/resources/db/migration/README.md)
  stays beside Flyway source. [Optional observability operations](../observability/README.md)
  stays with its Compose configuration.
- [Detailed verification plans and dated evidence](05-verification/22-test-strategy-and-acceptance.md)
  retain scoped records. A dated pass applies only to its recorded revision and
  environment; it is not current release acceptance.
- The [approved source PRD](02-requirements/09-product-requirements.md) remains
  operative for detailed product requirements and traceability until those
  obligations are explicitly reconciled into accepted OpenSpec specifications.

Approved V1 requirements remain operative while implementation is incomplete.
Product intent, source implementation, executed verification, and release
acceptance are separate. OpenSpec is initialized, and its main specifications
are still being established. The detailed V1 sources above remain operative
until their obligations are explicitly reconciled into accepted OpenSpec
specifications; initialization does not itself transfer behavioral authority.

## Browse the API locally

Use the [Swagger UI instructions in the root README](../README.md#swagger-ui).
The viewer renders the checked-in OpenAPI file; it does not provide a banking
backend.

## Validate documentation locally and in CI

With Python 3.12 and dependencies from
[`scripts/requirements-docs.txt`](../scripts/requirements-docs.txt) installed,
run these commands from the repository root:

```sh
python -m openapi_spec_validator docs/03-design/contracts/openapi-v1.yaml
python scripts/check_docs.py
docker compose -f compose.docs.yaml config --quiet
git diff --check
```

The [CI workflow](../.github/workflows/ci.yml) runs the documentation and
OpenAPI checks in `docs-validation`. These checks validate links and contract
structure; they do not establish runtime banking behavior or release acceptance.
