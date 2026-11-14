-- Lightweight community activity feed. Public content only; no account email or private metadata.
create table if not exists public.community_posts (
  id uuid primary key default gen_random_uuid(),
  author_id uuid not null references public.profiles(user_id) on delete cascade,
  body text not null check (char_length(body) between 1 and 280),
  created_at timestamptz not null default now()
);

create index if not exists community_posts_created_idx
  on public.community_posts(created_at desc);
create index if not exists community_posts_author_created_idx
  on public.community_posts(author_id, created_at desc);

alter table public.community_posts enable row level security;

create schema if not exists private;
create or replace function private.betalink_can_post_community()
returns boolean
language sql
stable
security definer
set search_path = ''
as $fn$
  select (select auth.uid()) is not null
    and not exists (
      select 1 from public.community_posts p
      where p.author_id = (select auth.uid())
        and p.created_at > now() - interval '60 seconds'
    )
    and (
      select count(*) from public.community_posts p
      where p.author_id = (select auth.uid())
        and p.created_at > now() - interval '24 hours'
    ) < 20;
$fn$;

revoke all on function private.betalink_can_post_community() from public, anon;
grant execute on function private.betalink_can_post_community() to authenticated;

drop policy if exists community_posts_read_authenticated on public.community_posts;
create policy community_posts_read_authenticated
on public.community_posts for select to authenticated
using (true);

drop policy if exists community_posts_insert_self_rate_limited on public.community_posts;
create policy community_posts_insert_self_rate_limited
on public.community_posts for insert to authenticated
with check (
  author_id = (select auth.uid())
  and (select private.betalink_can_post_community())
);

drop policy if exists community_posts_delete_self on public.community_posts;
create policy community_posts_delete_self
on public.community_posts for delete to authenticated
using (author_id = (select auth.uid()));

grant select on public.community_posts to authenticated;
grant insert (author_id, body) on public.community_posts to authenticated;
grant delete on public.community_posts to authenticated;
