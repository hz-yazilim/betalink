create or replace function private.betalink_community_post_body_allowed(p_body text)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select p_body is not null
    and length(btrim(p_body)) between 1 and 280
    and btrim(p_body) !~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$';
$$;

revoke all on function private.betalink_community_post_body_allowed(text) from public, anon;
grant execute on function private.betalink_community_post_body_allowed(text) to authenticated;

drop policy if exists community_posts_insert_self_rate_limited on public.community_posts;
create policy community_posts_insert_self_rate_limited
on public.community_posts
for insert
to authenticated
with check (
  author_id = (select auth.uid())
  and (select private.betalink_can_post_community())
  and (select private.betalink_community_post_body_allowed(body))
);
