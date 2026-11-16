-- Plantagotchi game state, one row per (anonymous) Supabase auth user.
-- Requires "Allow anonymous sign-ins" to be enabled under Authentication > Sign In / Providers.

create table if not exists public.game_state (
    player_id     uuid primary key default auth.uid() references auth.users (id) on delete cascade,
    lux           double precision not null default 100 check (lux between 0 and 100),
    water         double precision not null default 100 check (water between 0 and 100),
    love          double precision not null default 0   check (love between 0 and 100),
    fertilizer    double precision not null default 100 check (fertilizer between 0 and 100),
    coins         integer          not null default 0   check (coins >= 0),
    latitude      double precision not null default 0,
    longitude     double precision not null default 0,
    altitude      double precision not null default 0,
    updated_at_ms bigint           not null default 0,
    created_at    timestamptz      not null default now()
);

alter table public.game_state enable row level security;

create policy "Players read own state" on public.game_state
    for select to authenticated using ((select auth.uid()) = player_id);

create policy "Players insert own state" on public.game_state
    for insert to authenticated with check ((select auth.uid()) = player_id);

create policy "Players update own state" on public.game_state
    for update to authenticated
    using ((select auth.uid()) = player_id)
    with check ((select auth.uid()) = player_id);
