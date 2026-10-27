-- Order history: one row per order, always its latest state, built from the order photos
-- (OrderEvent.order) on cash.account-updates.v1. Display only; the cash owner is the source of truth.
create table orders (
    account_id            bigint      not null,
    client_order_id       varchar(48) not null,
    symbol                integer     not null,
    order_id              bigint      not null,          -- engine id (0 = not known yet)
    side                  varchar(4)  not null check (side in ('BUY', 'SELL')),
    order_type            varchar(10) not null,
    time_in_force         varchar(4)  not null,
    price_minor           bigint      not null,
    stop_price_minor      bigint,
    quantity              bigint      not null,
    filled_quantity       bigint      not null,
    filled_notional_minor bigint      not null,
    status                varchar(16) not null,
    reject_reason         varchar(32),                   -- public reject_reason word, REJECTED only
    created_at            timestamptz not null,          -- set by the first photo, never changed after
    updated_at            timestamptz not null,
    last_seq              bigint      not null,          -- account_seq of the update that last wrote this row
    primary key (account_id, client_order_id)
);

-- GET /v2/funds/orders: newest first, per account
create index orders_by_account_newest on orders (account_id, created_at desc, client_order_id desc);

-- Option C (decided 2026-10-27): a seq jump means some order photos may be missing.
-- null = no gap seen; otherwise the account_seq at which the jump was seen.
alter table balances add column history_gap_seq bigint;
