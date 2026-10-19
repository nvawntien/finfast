CREATE FUNCTION public.reject_financial_fact_mutation() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION '% on %.% is not allowed: financial facts are append-only',
        TG_OP, TG_TABLE_SCHEMA, TG_TABLE_NAME
        USING ERRCODE = '55000';
END;
$$;

-- Statement-level guards reject mutations even when the target table is empty.
-- INSERT remains available to future controlled posting and bootstrap transactions.
CREATE TRIGGER trg_financial_transaction_append_only
    BEFORE UPDATE OR DELETE OR TRUNCATE ON public.financial_transaction
    FOR EACH STATEMENT EXECUTE FUNCTION public.reject_financial_fact_mutation();

CREATE TRIGGER trg_ledger_entry_append_only
    BEFORE UPDATE OR DELETE OR TRUNCATE ON public.ledger_entry
    FOR EACH STATEMENT EXECUTE FUNCTION public.reject_financial_fact_mutation();
