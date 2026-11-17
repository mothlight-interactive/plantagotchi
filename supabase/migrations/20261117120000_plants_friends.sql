-- Multiple plants, daily reward streak and friends who can water each other's plants.

alter table public.game_state
    add column if not exists plants          jsonb   not null default '[]'::jsonb,
    add column if not exists active_plant_id text    not null default '',
    add column if not exists active_species  text    not null default 'SPROUT',
    add column if not exists active_stage    integer not null default 0,
    add column if not exists active_dead     boolean not null default false,
    add column if not exists last_reward_day bigint  not null default -1,
    add column if not exists streak          integer not null default 0,
    add column if not exists friend_code     text,
    add column if not exists friends         text[]  not null default '{}';

create unique index if not exists game_state_friend_code_key on public.game_state (friend_code);

-- Water sent by friends, claimed by the receiver on next sync.
create table if not exists public.gifts (
    id          bigint generated always as identity primary key,
    to_player   uuid not null references auth.users (id) on delete cascade,
    from_player uuid not null references auth.users (id) on delete cascade,
    kind        text not null default 'water' check (kind in ('water')),
    amount      double precision not null default 20 check (amount between 0 and 100),
    day         date not null default current_date,
    created_at  timestamptz not null default now(),
    unique (from_player, to_player, day)
);

alter table public.gifts enable row level security;

create policy "Players see gifts they received" on public.gifts
    for select to authenticated using ((select auth.uid()) = to_player);

create policy "Players claim gifts they received" on public.gifts
    for delete to authenticated using ((select auth.uid()) = to_player);

-- Public view of friends' plants by friend code (only what the game shows).
create or replace function public.friend_info(codes text[])
returns table (
    friend_code text,
    species     text,
    stage       integer,
    dead        boolean,
    health      double precision,
    water       double precision
)
language sql
stable
security definer
set search_path = ''
as $$
    select g.friend_code, g.active_species, g.active_stage, g.active_dead,
           (g.lux + g.water + g.love + g.fertilizer) / 4, g.water
    from public.game_state g
    where g.friend_code = any (codes)
    limit 50;
$$;

-- Send water to a friend, once per friend and day.
create or replace function public.send_water(code text)
returns text
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    receiver uuid;
begin
    if (select auth.uid()) is null then
        return 'not_signed_in';
    end if;
    select player_id into receiver from public.game_state where friend_code = upper(code);
    if receiver is null then
        return 'unknown_code';
    end if;
    if receiver = (select auth.uid()) then
        return 'own_code';
    end if;
    insert into public.gifts (to_player, from_player) values (receiver, (select auth.uid()))
    on conflict (from_player, to_player, day) do nothing;
    if not found then
        return 'already_sent_today';
    end if;
    return 'sent';
end;
$$;

revoke all on function public.friend_info(text[]) from public, anon;
revoke all on function public.send_water(text) from public, anon;
grant execute on function public.friend_info(text[]) to authenticated;
grant execute on function public.send_water(text) to authenticated;
