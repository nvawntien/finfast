CREATE TABLE public.app_user (
    id UUID NOT NULL,
    email_normalized VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'CUSTOMER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email UNIQUE (email_normalized),
    CONSTRAINT ck_app_user_email_nonblank CHECK (email_normalized ~ '[^[:space:]]'),
    CONSTRAINT ck_app_user_role CHECK (role IN ('CUSTOMER', 'ADMIN'))
);

CREATE TABLE public.customer (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    CONSTRAINT pk_customer PRIMARY KEY (id),
    CONSTRAINT uq_customer_user UNIQUE (user_id),
    CONSTRAINT fk_customer_user FOREIGN KEY (user_id) REFERENCES public.app_user (id),
    CONSTRAINT ck_customer_display_name_nonblank CHECK (display_name ~ '[^[:space:]]')
);

CREATE TABLE public.account (
    id UUID NOT NULL,
    account_number VARCHAR(32) NOT NULL,
    customer_id UUID,
    account_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    balance_vnd BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_account PRIMARY KEY (id),
    CONSTRAINT uq_account_number UNIQUE (account_number),
    CONSTRAINT uq_account_customer UNIQUE (customer_id),
    CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES public.customer (id),
    CONSTRAINT ck_account_number_nonblank CHECK (account_number ~ '[^[:space:]]'),
    CONSTRAINT ck_account_type CHECK (account_type IN ('CUSTOMER', 'TREASURY')),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'FROZEN')),
    CONSTRAINT ck_account_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_account_balance_nonnegative CHECK (balance_vnd >= 0),
    CONSTRAINT ck_account_ownership CHECK (
        (account_type = 'CUSTOMER' AND customer_id IS NOT NULL)
        OR (account_type = 'TREASURY' AND customer_id IS NULL)
    ),
    CONSTRAINT ck_account_treasury_active CHECK (account_type <> 'TREASURY' OR status = 'ACTIVE')
);

CREATE UNIQUE INDEX uq_account_single_treasury
    ON public.account (account_type) WHERE account_type = 'TREASURY';

CREATE TABLE public.financial_transaction (
    id UUID NOT NULL,
    reference VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    amount_vnd BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    commit_seq BIGINT NOT NULL,
    committed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_financial_transaction PRIMARY KEY (id),
    CONSTRAINT uq_financial_transaction_reference UNIQUE (reference),
    CONSTRAINT uq_financial_transaction_commit_seq UNIQUE (commit_seq),
    CONSTRAINT ck_financial_transaction_reference_nonblank CHECK (reference ~ '[^[:space:]]'),
    CONSTRAINT ck_financial_transaction_type CHECK (type IN ('INTERNAL_TRANSFER', 'DEMO_ALLOCATION', 'GENESIS')),
    CONSTRAINT ck_financial_transaction_amount_positive CHECK (amount_vnd > 0),
    CONSTRAINT ck_financial_transaction_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_financial_transaction_commit_seq_positive CHECK (commit_seq > 0)
);

CREATE UNIQUE INDEX uq_single_genesis
    ON public.financial_transaction (type) WHERE type = 'GENESIS';

CREATE TABLE public.ledger_entry (
    id UUID NOT NULL,
    transaction_id UUID NOT NULL,
    account_id UUID NOT NULL,
    delta_vnd BIGINT NOT NULL,
    CONSTRAINT pk_ledger_entry PRIMARY KEY (id),
    CONSTRAINT uq_ledger_entry_transaction_account UNIQUE (transaction_id, account_id),
    CONSTRAINT fk_ledger_entry_transaction FOREIGN KEY (transaction_id) REFERENCES public.financial_transaction (id),
    CONSTRAINT fk_ledger_entry_account FOREIGN KEY (account_id) REFERENCES public.account (id),
    CONSTRAINT ck_ledger_entry_delta_nonzero CHECK (delta_vnd <> 0)
);

CREATE INDEX idx_ledger_account_tx ON public.ledger_entry (account_id, transaction_id);

CREATE TABLE public.dataset_control (
    singleton_id SMALLINT NOT NULL,
    treasury_account_id UUID NOT NULL,
    genesis_tx_id UUID NOT NULL,
    initial_supply_vnd BIGINT NOT NULL,
    last_commit_seq BIGINT NOT NULL,
    CONSTRAINT pk_dataset_control PRIMARY KEY (singleton_id),
    CONSTRAINT fk_dataset_control_treasury FOREIGN KEY (treasury_account_id) REFERENCES public.account (id),
    CONSTRAINT fk_dataset_control_genesis FOREIGN KEY (genesis_tx_id) REFERENCES public.financial_transaction (id),
    CONSTRAINT ck_dataset_control_singleton CHECK (singleton_id = 1),
    CONSTRAINT ck_dataset_control_supply_positive CHECK (initial_supply_vnd > 0),
    CONSTRAINT ck_dataset_control_counter_nonnegative CHECK (last_commit_seq >= 0)
);

CREATE TABLE public.request_outcome (
    id UUID NOT NULL,
    initiator_user_id UUID NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(200) COLLATE "C" NOT NULL,
    canonicalization_version SMALLINT NOT NULL,
    canonical_intent JSONB NOT NULL,
    payload_fingerprint VARCHAR(64) NOT NULL,
    final_kind VARCHAR(16) NOT NULL,
    response_snapshot JSONB NOT NULL,
    rejection_code VARCHAR(64),
    transaction_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_request_outcome PRIMARY KEY (id),
    CONSTRAINT uq_request_outcome_scope UNIQUE (initiator_user_id, operation_type, idempotency_key),
    CONSTRAINT fk_request_outcome_initiator FOREIGN KEY (initiator_user_id) REFERENCES public.app_user (id),
    CONSTRAINT fk_request_outcome_transaction FOREIGN KEY (transaction_id) REFERENCES public.financial_transaction (id),
    CONSTRAINT ck_request_outcome_operation CHECK (operation_type IN ('INTERNAL_TRANSFER', 'DEMO_ALLOCATION')),
    CONSTRAINT ck_request_outcome_key_nonempty CHECK (char_length(idempotency_key) > 0),
    CONSTRAINT ck_request_outcome_canonicalization_positive CHECK (canonicalization_version > 0),
    CONSTRAINT ck_request_outcome_fingerprint CHECK (payload_fingerprint ~ '^[0-9A-Fa-f]{64}$'),
    CONSTRAINT ck_request_outcome_final_kind CHECK (final_kind IN ('COMPLETED', 'REJECTED')),
    CONSTRAINT ck_request_outcome_rejection_nonblank CHECK (rejection_code IS NULL OR rejection_code ~ '[^[:space:]]'),
    CONSTRAINT ck_request_outcome_final_shape CHECK (
        (final_kind = 'COMPLETED' AND transaction_id IS NOT NULL AND rejection_code IS NULL)
        OR (final_kind = 'REJECTED' AND transaction_id IS NULL AND rejection_code IS NOT NULL)
    )
);

CREATE TABLE public.admin_audit_event (
    id UUID NOT NULL,
    admin_user_id UUID NOT NULL,
    target_account_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    transaction_id UUID,
    before_status VARCHAR(16),
    after_status VARCHAR(16),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_admin_audit_event PRIMARY KEY (id),
    CONSTRAINT uq_admin_audit_transaction UNIQUE (transaction_id),
    CONSTRAINT fk_admin_audit_admin FOREIGN KEY (admin_user_id) REFERENCES public.app_user (id),
    CONSTRAINT fk_admin_audit_target FOREIGN KEY (target_account_id) REFERENCES public.account (id),
    CONSTRAINT fk_admin_audit_transaction FOREIGN KEY (transaction_id) REFERENCES public.financial_transaction (id),
    CONSTRAINT ck_admin_audit_action CHECK (action IN ('DEMO_ALLOCATION', 'FREEZE_ACCOUNT', 'UNFREEZE_ACCOUNT')),
    CONSTRAINT ck_admin_audit_outcome CHECK (outcome IN ('SUCCESS', 'UNCHANGED')),
    CONSTRAINT ck_admin_audit_reason_nonblank CHECK (reason ~ '[^[:space:]]'),
    CONSTRAINT ck_admin_audit_before_status CHECK (before_status IN ('ACTIVE', 'FROZEN')),
    CONSTRAINT ck_admin_audit_after_status CHECK (after_status IN ('ACTIVE', 'FROZEN')),
    CONSTRAINT ck_admin_audit_shape CHECK (
        (
            action = 'DEMO_ALLOCATION' AND outcome = 'SUCCESS'
            AND transaction_id IS NOT NULL AND before_status IS NULL AND after_status IS NULL
        )
        OR (
            action IN ('FREEZE_ACCOUNT', 'UNFREEZE_ACCOUNT') AND transaction_id IS NULL
            AND before_status IS NOT NULL AND after_status IS NOT NULL
            AND (
                (outcome = 'SUCCESS' AND action = 'FREEZE_ACCOUNT' AND before_status = 'ACTIVE' AND after_status = 'FROZEN')
                OR (outcome = 'SUCCESS' AND action = 'UNFREEZE_ACCOUNT' AND before_status = 'FROZEN' AND after_status = 'ACTIVE')
                OR (outcome = 'UNCHANGED' AND before_status = after_status)
            )
        )
    )
);

CREATE INDEX idx_admin_audit_created ON public.admin_audit_event (created_at DESC, id DESC);

-- Retain account identities so a deleted row cannot release its number for reuse.
CREATE FUNCTION public.protect_account_identity() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF ROW(NEW.id, NEW.account_number, NEW.account_type, NEW.customer_id)
            IS DISTINCT FROM ROW(OLD.id, OLD.account_number, OLD.account_type, OLD.customer_id) THEN
            RAISE EXCEPTION 'Account identity and ownership are immutable'
                USING ERRCODE = '55000';
        END IF;
        RETURN NEW;
    END IF;
    RAISE EXCEPTION '% on account is not allowed: account identities and numbers must be retained', TG_OP
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_account_identity
    BEFORE UPDATE OR DELETE ON public.account
    FOR EACH ROW EXECUTE FUNCTION public.protect_account_identity();

CREATE TRIGGER trg_account_no_truncate
    BEFORE TRUNCATE ON public.account
    FOR EACH STATEMENT EXECUTE FUNCTION public.protect_account_identity();

-- Type checks are immediate on bootstrap insertion; counter-only updates do not read Accounts.
CREATE FUNCTION public.protect_dataset_control() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF ROW(NEW.singleton_id, NEW.treasury_account_id, NEW.genesis_tx_id, NEW.initial_supply_vnd)
            IS DISTINCT FROM ROW(OLD.singleton_id, OLD.treasury_account_id, OLD.genesis_tx_id, OLD.initial_supply_vnd) THEN
            RAISE EXCEPTION 'Dataset identity, Treasury, GENESIS and initial supply are immutable'
                USING ERRCODE = '55000';
        END IF;
        -- Check exhaustion before addition so BIGINT arithmetic cannot overflow here.
        IF OLD.last_commit_seq = 9223372036854775807 THEN
            RAISE EXCEPTION 'Dataset last_commit_seq is exhausted (BIGINT maximum reached)'
                USING ERRCODE = '23514';
        END IF;
        IF NEW.last_commit_seq IS DISTINCT FROM OLD.last_commit_seq + 1 THEN
            RAISE EXCEPTION 'Dataset last_commit_seq must increase by exactly 1'
                USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    ELSIF TG_OP = 'INSERT' THEN
        IF NOT EXISTS (
            SELECT 1 FROM public.account WHERE id = NEW.treasury_account_id AND account_type = 'TREASURY'
        ) THEN
            RAISE EXCEPTION 'Dataset treasury_account_id must reference a TREASURY account'
                USING ERRCODE = '23514';
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM public.financial_transaction WHERE id = NEW.genesis_tx_id AND type = 'GENESIS'
        ) THEN
            RAISE EXCEPTION 'Dataset genesis_tx_id must reference a GENESIS transaction'
                USING ERRCODE = '23514';
        END IF;
        IF NEW.last_commit_seq IS DISTINCT FROM (
            SELECT commit_seq FROM public.financial_transaction WHERE id = NEW.genesis_tx_id
        ) THEN
            RAISE EXCEPTION 'Dataset last_commit_seq must match the GENESIS commit_seq'
                USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;
    RAISE EXCEPTION '% on dataset_control is not allowed: initialized dataset configuration must be retained', TG_OP
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_dataset_control_configuration
    BEFORE INSERT OR UPDATE OR DELETE ON public.dataset_control
    FOR EACH ROW EXECUTE FUNCTION public.protect_dataset_control();

CREATE TRIGGER trg_dataset_control_no_truncate
    BEFORE TRUNCATE ON public.dataset_control
    FOR EACH STATEMENT EXECUTE FUNCTION public.protect_dataset_control();
