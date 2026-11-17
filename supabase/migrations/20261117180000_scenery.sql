-- Purchasable sceneries and grounds; species names grow with the game (stored inside plants jsonb).
alter table public.game_state
    add column if not exists owned_scenery text[] not null default '{MOUNTAINS}',
    add column if not exists scenery       text   not null default 'MOUNTAINS',
    add column if not exists owned_grounds text[] not null default '{GRASS}',
    add column if not exists ground        text   not null default 'GRASS';
