-- Funds' read-only copy of each account's cash, built from cash.account-updates.v1.
-- Display only (wallet / profile pages); the cash owner is the source of truth.
create table balances (
    account_id          bigint      primary key,
    cash_free_minor     bigint      not null,
    cash_locked_minor   bigint      not null,
    realized_pnl_minor  bigint      not null,
    last_applied_seq    bigint      not null check (last_applied_seq >= 0),  -- account_seq of the update shown here
    updated_at          timestamptz not null                                   -- AccountUpdate.ts of that update
);
