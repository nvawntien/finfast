package com.finfast;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Empty tables and fresh Flyway migrate/validate are covered by PostgresIntegrationTests.
class SchemaStructureTests extends RollbackDatabaseTest {

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            app_user|pk_app_user|p|PRIMARY KEY (id)
            customer|pk_customer|p|PRIMARY KEY (id)
            account|pk_account|p|PRIMARY KEY (id)
            financial_transaction|pk_financial_transaction|p|PRIMARY KEY (id)
            ledger_entry|pk_ledger_entry|p|PRIMARY KEY (id)
            dataset_control|pk_dataset_control|p|PRIMARY KEY (singleton_id)
            request_outcome|pk_request_outcome|p|PRIMARY KEY (id)
            admin_audit_event|pk_admin_audit_event|p|PRIMARY KEY (id)
            app_user|uq_app_user_email|u|UNIQUE (email_normalized)
            customer|uq_customer_user|u|UNIQUE (user_id)
            account|uq_account_number|u|UNIQUE (account_number)
            account|uq_account_customer|u|UNIQUE (customer_id)
            financial_transaction|uq_financial_transaction_reference|u|UNIQUE (reference)
            financial_transaction|uq_financial_transaction_commit_seq|u|UNIQUE (commit_seq)
            ledger_entry|uq_ledger_entry_transaction_account|u|UNIQUE (transaction_id, account_id)
            request_outcome|uq_request_outcome_scope|u|UNIQUE (initiator_user_id, operation_type, idempotency_key)
            admin_audit_event|uq_admin_audit_transaction|u|UNIQUE (transaction_id)
            customer|fk_customer_user|f|FOREIGN KEY (user_id) REFERENCES app_user(id)
            account|fk_account_customer|f|FOREIGN KEY (customer_id) REFERENCES customer(id)
            ledger_entry|fk_ledger_entry_transaction|f|FOREIGN KEY (transaction_id) REFERENCES financial_transaction(id)
            ledger_entry|fk_ledger_entry_account|f|FOREIGN KEY (account_id) REFERENCES account(id)
            dataset_control|fk_dataset_control_treasury|f|FOREIGN KEY (treasury_account_id) REFERENCES account(id)
            dataset_control|fk_dataset_control_genesis|f|FOREIGN KEY (genesis_tx_id) REFERENCES financial_transaction(id)
            request_outcome|fk_request_outcome_initiator|f|FOREIGN KEY (initiator_user_id) REFERENCES app_user(id)
            request_outcome|fk_request_outcome_transaction|f|FOREIGN KEY (transaction_id) REFERENCES financial_transaction(id)
            admin_audit_event|fk_admin_audit_admin|f|FOREIGN KEY (admin_user_id) REFERENCES app_user(id)
            admin_audit_event|fk_admin_audit_target|f|FOREIGN KEY (target_account_id) REFERENCES account(id)
            admin_audit_event|fk_admin_audit_transaction|f|FOREIGN KEY (transaction_id) REFERENCES financial_transaction(id)
            app_user|ck_app_user_email_nonblank|c|email_normalized;[^[:space:]]
            app_user|ck_app_user_role|c|role;'CUSTOMER';'ADMIN'
            customer|ck_customer_display_name_nonblank|c|display_name;[^[:space:]]
            account|ck_account_number_nonblank|c|account_number;[^[:space:]]
            account|ck_account_type|c|account_type;'CUSTOMER';'TREASURY'
            account|ck_account_status|c|status;'ACTIVE';'FROZEN'
            account|ck_account_currency|c|currency;'VND'
            account|ck_account_balance_nonnegative|c|balance_vnd >= 0
            account|ck_account_ownership|c|account_type;'CUSTOMER';'TREASURY';customer_id IS NOT NULL;customer_id IS NULL
            account|ck_account_treasury_active|c|account_type;'TREASURY';status;'ACTIVE'
            financial_transaction|ck_financial_transaction_reference_nonblank|c|reference;[^[:space:]]
            financial_transaction|ck_financial_transaction_type|c|type;'INTERNAL_TRANSFER';'DEMO_ALLOCATION';'GENESIS'
            financial_transaction|ck_financial_transaction_amount_positive|c|amount_vnd > 0
            financial_transaction|ck_financial_transaction_currency|c|currency;'VND'
            financial_transaction|ck_financial_transaction_commit_seq_positive|c|commit_seq > 0
            ledger_entry|ck_ledger_entry_delta_nonzero|c|delta_vnd <> 0
            dataset_control|ck_dataset_control_singleton|c|singleton_id = 1
            dataset_control|ck_dataset_control_supply_positive|c|initial_supply_vnd > 0
            dataset_control|ck_dataset_control_counter_nonnegative|c|last_commit_seq >= 0
            request_outcome|ck_request_outcome_operation|c|operation_type;'INTERNAL_TRANSFER';'DEMO_ALLOCATION'
            request_outcome|ck_request_outcome_key_nonempty|c|char_length;idempotency_key;> 0
            request_outcome|ck_request_outcome_canonicalization_positive|c|canonicalization_version > 0
            request_outcome|ck_request_outcome_fingerprint|c|payload_fingerprint;^[0-9A-Fa-f]{64}$
            request_outcome|ck_request_outcome_final_kind|c|final_kind;'COMPLETED';'REJECTED'
            request_outcome|ck_request_outcome_rejection_nonblank|c|rejection_code IS NULL;[^[:space:]]
            request_outcome|ck_request_outcome_final_shape|c|'COMPLETED';transaction_id IS NOT NULL;rejection_code IS NULL;'REJECTED';transaction_id IS NULL;rejection_code IS NOT NULL
            admin_audit_event|ck_admin_audit_action|c|action;'DEMO_ALLOCATION';'FREEZE_ACCOUNT';'UNFREEZE_ACCOUNT'
            admin_audit_event|ck_admin_audit_outcome|c|outcome;'SUCCESS';'UNCHANGED'
            admin_audit_event|ck_admin_audit_reason_nonblank|c|reason;[^[:space:]]
            admin_audit_event|ck_admin_audit_before_status|c|before_status;'ACTIVE';'FROZEN'
            admin_audit_event|ck_admin_audit_after_status|c|after_status;'ACTIVE';'FROZEN'
            admin_audit_event|ck_admin_audit_shape|c|before_status IS NOT NULL;after_status IS NOT NULL;'DEMO_ALLOCATION';'FREEZE_ACCOUNT';'UNFREEZE_ACCOUNT';'SUCCESS';'UNCHANGED'
            """)
    void namedConstraintsHaveExpectedDefinitions(String table, String name, String type, String definition) {
        Map<String, Object> constraint = jdbc.queryForMap("""
                SELECT contype::text AS kind, convalidated AS validated, condeferrable AS deferrable,
                       pg_get_constraintdef(oid) AS definition, confdeltype::text AS delete_action,
                       confupdtype::text AS update_action
                FROM pg_constraint WHERE conrelid = to_regclass(?) AND conname = ?
                """, "public." + table, name);
        assertThat(constraint.get("kind")).isEqualTo(type);
        assertThat(constraint.get("validated")).isEqualTo(true);
        assertThat(constraint.get("deferrable")).isEqualTo(false);
        assertThat((String) constraint.get("definition")).contains(definition.split(";"));
        if (type.equals("f")) {
            assertThat(constraint.get("delete_action")).isEqualTo("a");
            assertThat(constraint.get("update_action")).isEqualTo("a");
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            uq_account_single_treasury|account|true|account_type|'TREASURY'
            uq_single_genesis|financial_transaction|true|type|'GENESIS'
            idx_ledger_account_tx|ledger_entry|false|account_id, transaction_id|
            idx_admin_audit_created|admin_audit_event|false|created_at DESC, id DESC|
            """)
    void approvedAdditionalIndexesHaveExpectedKeysAndPredicates(
            String name, String table, boolean unique, String keys, String predicate) {
        Map<String, Object> index = jdbc.queryForMap("""
                SELECT i.indisunique AS is_unique, i.indisvalid AS valid, pg_get_indexdef(i.indexrelid) AS definition,
                       pg_get_expr(i.indpred, i.indrelid) AS predicate
                FROM pg_index i
                WHERE i.indexrelid = to_regclass(?) AND i.indrelid = to_regclass(?)
                """, "public." + name, "public." + table);
        assertThat(index.get("is_unique")).isEqualTo(unique);
        assertThat(index.get("valid")).isEqualTo(true);
        assertThat((String) index.get("definition")).contains("(" + keys + ")");
        if (predicate == null) {
            assertThat(index.get("predicate")).isNull();
        } else {
            assertThat((String) index.get("predicate")).contains(predicate);
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            account|trg_account_identity|protect_account_identity|27
            account|trg_account_no_truncate|protect_account_identity|34
            dataset_control|trg_dataset_control_configuration|protect_dataset_control|31
            dataset_control|trg_dataset_control_no_truncate|protect_dataset_control|34
            financial_transaction|trg_financial_transaction_append_only|reject_financial_fact_mutation|58
            ledger_entry|trg_ledger_entry_append_only|reject_financial_fact_mutation|58
            """)
    void approvedTriggersAreEnabledWithCorrectTimingEventsAndFunction(String table, String name, String function, int type) {
        Map<String, Object> trigger = jdbc.queryForMap("""
                SELECT t.tgenabled::text AS enabled, t.tgtype::int AS type, p.proname AS function,
                       t.tgisinternal AS internal, pg_get_triggerdef(t.oid) AS definition
                FROM pg_trigger t JOIN pg_proc p ON p.oid = t.tgfoid
                WHERE t.tgrelid = to_regclass(?) AND t.tgname = ?
                """, "public." + table, name);
        assertThat(trigger.get("enabled")).isEqualTo("O");
        assertThat(trigger.get("internal")).isEqualTo(false);
        // pg_trigger bits: ROW=1, BEFORE=2, INSERT=4, DELETE=8, UPDATE=16, TRUNCATE=32.
        assertThat(trigger.get("type")).isEqualTo(type);
        assertThat(trigger.get("function")).isEqualTo(function);
        assertThat((String) trigger.get("definition")).contains("BEFORE", "EXECUTE FUNCTION " + function + "()");
    }

    @ParameterizedTest
    @ValueSource(strings = {"protect_account_identity", "protect_dataset_control", "reject_financial_fact_mutation"})
    void triggerFunctionsArePlpgsqlInvokerFunctions(String name) {
        Map<String, Object> function = jdbc.queryForMap("""
                SELECT l.lanname AS language, p.prosecdef AS security_definer, p.prorettype::regtype::text AS result
                FROM pg_proc p JOIN pg_language l ON l.oid = p.prolang
                WHERE p.oid = to_regprocedure(?)
                """, "public." + name + "()");
        assertThat(function).containsEntry("language", "plpgsql").containsEntry("security_definer", false)
                .containsEntry("result", "trigger");
    }
}
