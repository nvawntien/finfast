# Database migrations

Flyway owns schema changes in this directory and runs automatically at backend
startup. Step 3 deliberately contains no versioned SQL migrations or business
tables. Flyway initializes its own `flyway_schema_history` table on an empty
PostgreSQL database; zero applied and zero pending migrations are expected.

In Step 4, add the first reviewed, meaningful schema migration as
`V1__<description>.sql` here, then commit it with the corresponding tests. Use
unique, increasing versions (`V2__<description>.sql`, and so on) for later changes;
coordinate versions across branches before merging. Update the smoke test's
empty-schema assertions when the first real migration is introduced.

Do not edit or delete migrations that have already been applied in normal
development. Add a new migration instead. Do not add dummy migrations, seed
banking data, run Flyway clean, or use Hibernate schema auto-update. Never reset
another developer's database to resolve a migration conflict.
