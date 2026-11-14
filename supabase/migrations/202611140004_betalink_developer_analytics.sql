-- Owner-scoped aggregate analytics; never returns individual tester identities.
create or replace function private.betalink_developer_analytics()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $fn$
  select case when (select auth.uid()) is null then null else jsonb_build_object(
    'campaigns', (select count(*) from public.campaigns c where c.developer_id = (select auth.uid())),
    'open_campaigns', (select count(*) from public.campaigns c where c.developer_id = (select auth.uid()) and c.status = 'open'),
    'completed_campaigns', (select count(*) from public.campaigns c where c.developer_id = (select auth.uid()) and c.status = 'completed'),
    'active_testers', (
      select count(*) from public.campaign_members m
      join public.campaigns c on c.id = m.campaign_id
      where c.developer_id = (select auth.uid()) and m.status in ('accepted','opted_in','active')
    ),
    'completed_tests', (
      select count(*) from public.campaign_members m
      join public.campaigns c on c.id = m.campaign_id
      where c.developer_id = (select auth.uid()) and m.status = 'completed'
    ),
    'dropped_testers', (
      select count(*) from public.campaign_members m
      join public.campaigns c on c.id = m.campaign_id
      where c.developer_id = (select auth.uid()) and m.status = 'dropped'
    ),
    'feedback_count', (
      select count(*) from public.feedback f
      join public.campaigns c on c.id = f.campaign_id
      where c.developer_id = (select auth.uid())
    ),
    'bug_reports', (
      select count(*) from public.bug_reports b
      join public.campaigns c on c.id = b.campaign_id
      where c.developer_id = (select auth.uid())
    ),
    'average_rating', (
      select round(avg(f.rating), 2) from public.feedback f
      join public.campaigns c on c.id = f.campaign_id
      where c.developer_id = (select auth.uid())
    )
  ) end;
$fn$;

create or replace function public.betalink_developer_analytics()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $fn$
  select private.betalink_developer_analytics();
$fn$;

revoke all on function private.betalink_developer_analytics() from public, anon;
grant execute on function private.betalink_developer_analytics() to authenticated;
revoke all on function public.betalink_developer_analytics() from public, anon;
grant execute on function public.betalink_developer_analytics() to authenticated;
