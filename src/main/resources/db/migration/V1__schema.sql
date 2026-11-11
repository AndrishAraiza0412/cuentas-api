-- All amounts are bigint centavos (MXN). The balance check is the last line of defence
-- against overdrafts; the service validates first and returns a clear error.

create table users (
    id            bigserial primary key,
    email         text   not null unique,
    password_hash text   not null,
    role          text   not null
);

create sequence account_number_seq start 100000000001;

create table accounts (
    id      bigserial primary key,
    user_id bigint not null references users (id),
    number  text   not null unique,
    balance bigint not null check (balance >= 0),
    status  text   not null default 'ACTIVE',
    version bigint not null default 0
);
create index accounts_user_id_idx on accounts (user_id);

create table transfers (
    id                bigserial primary key,
    source_account_id bigint      not null references accounts (id),
    target_account_id bigint      not null references accounts (id),
    amount            bigint      not null check (amount > 0),
    status            text        not null,
    created_at        timestamptz not null
);

create table ledger_entries (
    id            bigserial primary key,
    account_id    bigint      not null references accounts (id),
    transfer_id   bigint      references transfers (id),
    type          text        not null,
    amount        bigint      not null check (amount > 0),
    balance_after bigint      not null,
    created_at    timestamptz not null
);
create index ledger_entries_account_id_idx on ledger_entries (account_id, id);

create table idempotency_keys (
    id              bigserial primary key,
    user_id         bigint not null references users (id),
    key             text   not null,
    request_hash    text   not null,
    response_status int,
    response_body   text,
    unique (user_id, key)
);

create table audit_log (
    id         bigserial primary key,
    actor_id   bigint,
    action     text        not null,
    target     text,
    outcome    text        not null,
    created_at timestamptz not null
);
create index audit_log_created_at_idx on audit_log (created_at);
