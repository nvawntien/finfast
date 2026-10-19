# Database migrations

Flyway is the schema source of truth and runs automatically at Spring Boot startup.
The concrete schema decisions approved in Step 4B.1 take precedence over older
`PROPOSED` labels for those same decisions in design documents. The transactional
global commit counter remains provisional and requires performance benchmarking.

## Versions and tables

| Migration | Purpose |
|---|---|
| `V1__create_banking_schema.sql` | Eight tables, named constraints, four approved indexes and identity/configuration guards |
| `V2__protect_financial_facts.sql` | Append-only guards for Financial Transactions and Ledger Entries |

| Table | Structural guarantees |
|---|---|
| `app_user` | UUID PK; unique nonblank stored email; CUSTOMER/ADMIN role, default CUSTOMER |
| `customer` | UUID PK; unique User FK; nonblank display name |
| `account` | Unique immutable number; at most one Account per Customer; type/owner compatibility; ACTIVE/FROZEN, VND, nonnegative balance; Treasury always ACTIVE |
| `financial_transaction` | Unique reference and positive commit sequence; allowed type, positive amount and VND |
| `ledger_entry` | Transaction/Account FKs; nonzero delta; unique Transaction/Account pair |
| `dataset_control` | Singleton ID 1; Treasury/GENESIS FKs; positive immutable initial supply; counter initialized from GENESIS and advanced exactly by one |
| `request_outcome` | Scoped uniqueness; final COMPLETED/REJECTED shape; operation, canonicalization and fingerprint checks |
| `admin_audit_event` | Actor/target/transaction FKs; unique optional transaction; valid action/outcome/reason and allocation/status-change shapes |

All tables are in `public`. Flyway also maintains its own `flyway_schema_history`;
it is not a banking table. Successful initialization applies versions 1 and 2,
leaves no pending migrations and creates **no banking rows**. There are no session
tables, sample identities, Treasury, GENESIS or other seed records.

FKs are immediate, non-cascading and non-deferrable. Creation order is User →
Customer → Account → Financial Transaction → Ledger Entry → Dataset Control →
Request Outcome → Admin Audit Event. There are no circular FKs or extra uniqueness
indexes on the singleton's links. PK/UNIQUE constraints create their own indexes.

Only these four additional indexes are defined:

- `uq_account_single_treasury`: unique partial index for TREASURY Accounts.
- `uq_single_genesis`: unique partial index for GENESIS Financial Transactions.
- `idx_ledger_account_tx`: `(account_id, transaction_id)` for Account history.
- `idx_admin_audit_created`: `(created_at DESC, id DESC)` for audit ordering.

## Field bounds and structural guards

- Idempotency keys are 1–200 characters, matching the existing OpenAPI header
  bound. `VARCHAR(200) COLLATE "C"` preserves case-sensitive, exact stored-value
  equality, including whitespace. Never trim, normalize or truncate keys; reject
  oversized input before persistence. Ordinary overlength inserts fail rather
  than truncating; explicit casts must not be used to truncate application input.
- Canonicalization versions are positive SMALLINTs. Fingerprints are exactly 64
  hexadecimal characters (either case), suitable for SHA-256. JSONB is NOT NULL;
  canonical/response JSON shapes, full-intent comparison and sensitive-field
  exclusion remain application responsibilities, not hash-only comparisons.
- Blank checks use PostgreSQL's POSIX whitespace class; stored values are not
  automatically trimmed. Email normalization remains an application concern.
- Account `id`, `account_number`, `account_type` and `customer_id` are fixed after insertion.
  `protect_account_identity` rejects identity/ownership changes, row deletion and truncation.
  Retaining Account rows prevents number reassignment without a ninth tombstone
  table. Balance/status updates remain possible within the row constraints.
- `protect_dataset_control` checks on INSERT that the two references have Treasury
  and GENESIS types and `last_commit_seq` equals that GENESIS's `commit_seq`.
  Account type immutability and V2 fact immutability keep these
  links valid. Updates cannot alter the singleton ID, references or initial supply;
  DELETE/TRUNCATE are rejected. Every UPDATE must advance `last_commit_seq` by
  exactly one; exhaustion is rejected before BIGINT addition can overflow in the guard.
  Counter-only updates do not read or lock Accounts, preserving the future
  Account-before-dataset lock order. No posting/counter-allocation algorithm is
  implemented, and the counter is not a PostgreSQL sequence.
- Audit status fields must both be present for status operations. SUCCESS requires
  ACTIVE → FROZEN or FROZEN → ACTIVE for the corresponding action; UNCHANGED permits
  equal valid statuses. Allocation requires SUCCESS, a transaction and no status
  fields. Explicit null checks prevent SQL CHECK's NULL result from admitting
  incomplete status records. Rejection codes must be nonblank when present.
- UUIDs and posting timestamps have no generated defaults. Only `app_user.created_at`
  defaults to the current transaction timestamp. `committed_at` is the recorded
  posting time, not the exact physical PostgreSQL commit instant.

## Append-only financial facts

V2 installs `trg_financial_transaction_append_only` and
`trg_ledger_entry_append_only`. Both call the invoker-rights PL/pgSQL function
`reject_financial_fact_mutation`, which raises descriptive SQLSTATE `55000`
exceptions for UPDATE, DELETE and TRUNCATE. These immediate BEFORE STATEMENT
guards also reject commands targeting zero rows. INSERT is allowed. There is no
dynamic SQL and no deferred cross-row financial constraint trigger.

These guards are defense in depth. The current local development role is also the
database owner and Flyway runs at application startup. A sufficiently privileged
owner can disable/drop triggers or alter tables. Separate restricted runtime and
migration roles require a later reviewed infrastructure change; this migration
does not provision roles or redesign Docker credentials.

## Application invariants still required

Row constraints and append-only guards do not establish correct financial posting:

- Exactly two equal-and-opposite entries for transfers/allocations; one positive
  Treasury entry for GENESIS; entry amounts/types and Account roles must agree.
- Stored balances must reconcile with ledger deltas; total supply and GENESIS
  amount must match `initial_supply_vnd`. No schema CHECK proves these cross-row facts.
- Authorization must establish CUSTOMER ownership and ADMIN actors/targets, validate
  ACTIVE status/funds under deterministic Account locks and reject self-transfers.
  FKs do not prevent an ADMIN User from having a Customer profile, require an audit
  actor to have ADMIN role, or require an audited target to be a CUSTOMER Account.
- Balance changes, posting facts, final outcomes and required success audit must
  commit atomically. SUCCESS must only be reported after commit confirmation.
- Idempotency requires transaction-scoped advisory coordination, full canonical
  intent comparison, immutable final results and dataset-lifetime retention; the
  UNIQUE constraint alone does not implement replay, retries or authorization.
- The future posting path must increment the global counter transactionally under
  the dataset row lock and match it to `commit_seq`. No `nextval()` ordering shortcut
  is supplied. Commit-order correctness and counter contention require later tests
  and benchmarks; numeric overflow must also be handled by application code.

GENESIS is a separate controlled bootstrap operation. A future atomic bootstrap
can insert Treasury, its GENESIS transaction, positive Ledger Entry and then Dataset
Control in one transaction with explicit supply and counter values. It must verify
their financial consistency; migrations neither initialize nor reseed the dataset.

## Database integration tests

From `backend/`, with Java 21 and a Testcontainers-compatible container runtime:

```sh
./mvnw test
./mvnw package
```

The tests reuse the pinned PostgreSQL 16.15 Testcontainers configuration. No host
PostgreSQL or manually started Compose stack is needed. Container connection details
override local datasource settings; tests never target the persistent developer database.

| Test class | Coverage |
|---|---|
| `PostgresIntegrationTests` | Fresh V1/V2 migration and validation, eight empty banking tables, connectivity |
| `SchemaStructureTests` | Named PK/FK/UNIQUE/CHECK definitions, four approved indexes, enabled triggers/functions |
| `IdentityAndAccountConstraintTests` | User/profile constraints, Account ownership/type/status/currency/balance rules |
| `SchemaIdentityAndCounterTests` | Immutable Account identity/ownership, removal guards, immutable dataset configuration, exact counter increments, exhaustion and GENESIS consistency |
| `FinancialTransactionAndLedgerTests` | Financial row constraints, uniqueness/FKs and UPDATE/DELETE/TRUNCATE append-only guards, including zero-match statements |
| `DatasetControlConstraintTests` | Singleton, positive supply, reference types, counter rejection and removal guards |
| `RequestOutcomeConstraintTests` | Scoped case-sensitive opaque keys, field bounds, final-outcome shapes and JSONB round trips |
| `AdminAuditConstraintTests` | Allocation/status audit shapes, common constraints, FKs and unique audited transaction |
| `DatabaseAtomicityAndConcurrencyTests` | Four explicit rollback scenarios, concurrent counter increments and concurrent outcome uniqueness |

Ordinary constraint tests roll back fixtures; savepoints recover expected PostgreSQL
statement errors without disabling triggers. Commit/concurrency tests create a separate
database per test inside the disposable Testcontainer, commit setup explicitly, control
JDBC commit/rollback and verify results from independent connections. Testcontainer
teardown removes those databases without modifying developer volumes or running clean.
Concurrency tests use latches, bounded database/network/test timeouts and observed
[`pg_blocking_pids`](https://www.postgresql.org/docs/16/functions-info.html#FUNCTIONS-INFO-SESSION-TABLE)
waits, with worker failures propagated; they use no scheduling sleeps or stress workload.

These tests exercise database-enforced PK/FK/UNIQUE/CHECK constraints, partial unique
indexes, Account identity/ownership immutability, dataset rules and append-only facts.
Rollback tests demonstrate PostgreSQL transaction behavior using test-only SQL, not
implemented posting services. Counter serialization does not prove account-lock ordering;
the uniqueness race does not implement or prove advisory-lock request coordination.
Balanced paired entries, ledger/balance reconciliation, service-level atomic outcomes,
authorization, mandatory success audits, complete controlled GENESIS and counter
performance remain responsibilities of future application tests and benchmarks.

## Adding future migrations safely

Add the next unique increasing version, for example `V3__<description>.sql`, and
coordinate versions across branches before merging. Flyway applies pending versions
and validates checksums of applied versions. Do not edit/delete an applied migration,
use repeatable scripts for this initial schema, enable Hibernate auto-update, run
Flyway clean or reset a persistent database to resolve a migration conflict.

Use disposable PostgreSQL 16 Testcontainers for validation (`cd backend && ./mvnw test`
and `./mvnw package`, with Java 21 and a working container runtime). Do not point
checks at a developer's persistent database. If existing objects or Flyway history
are unexpected, stop and report them; do not drop, truncate, clean or silently
baseline the database. A planned empty-schema upgrade must preserve Flyway history.
