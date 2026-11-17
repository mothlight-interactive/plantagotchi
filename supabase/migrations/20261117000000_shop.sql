-- Shop: owned pots, selected pot and grow lamp booster.
alter table public.game_state
    add column if not exists owned_pots    text[] not null default '{pot12}',
    add column if not exists selected_pot  text   not null default 'pot12',
    add column if not exists lamp_until_ms bigint not null default 0;
