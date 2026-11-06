-- Keep privileged RPC implementation in a non-exposed schema.
create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

create or replace function private.confirm_betalink_play_opt_in(p_campaign_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;
  update public.campaign_members
     set status = 'opted_in',
         opted_in_at = coalesce(opted_in_at, now()),
         last_confirmed_at = now()
   where campaign_id = p_campaign_id
     and tester_id = auth.uid()
     and status in ('accepted','opted_in','active');
  if not found then
    raise exception 'No accepted application found for this campaign' using errcode = 'P0002';
  end if;
end;
$$;
revoke all on function private.confirm_betalink_play_opt_in(uuid) from public, anon;
grant execute on function private.confirm_betalink_play_opt_in(uuid) to authenticated;

create or replace function public.confirm_betalink_play_opt_in(p_campaign_id uuid)
returns void
language sql
security invoker
set search_path = public, pg_temp
as $$
  select private.confirm_betalink_play_opt_in(p_campaign_id);
$$;
revoke all on function public.confirm_betalink_play_opt_in(uuid) from public, anon;
grant execute on function public.confirm_betalink_play_opt_in(uuid) to authenticated;

create or replace function private.get_betalink_campaign_tracking(p_campaign_id uuid)
returns table (
  tracking_status text,
  started_at timestamptz,
  required_days integer,
  elapsed_days integer,
  active_testers integer,
  required_testers integer
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  uid uuid := auth.uid();
  current_tracking public.campaign_tracking%rowtype;
  active_count integer;
begin
  if uid is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;
  if not exists (
    select 1 from public.campaign_members m
    where m.campaign_id = p_campaign_id
      and m.tester_id = uid
      and m.status in ('accepted','opted_in','active','completed')
  ) and not exists (
    select 1 from public.campaigns c
    where c.id = p_campaign_id and c.developer_id = uid
  ) then
    raise exception 'Not a campaign participant' using errcode = '42501';
  end if;
  select * into current_tracking
  from public.campaign_tracking
  where campaign_id = p_campaign_id;
  if not found then
    raise exception 'Tracking record not found' using errcode = 'P0002';
  end if;
  select count(*)::integer into active_count
  from public.campaign_members
  where campaign_id = p_campaign_id
    and status in ('opted_in','active')
    and opted_in_at is not null;
  return query select
    case
      when current_tracking.tracking_status = 'running'
       and current_tracking.started_at <= now() - make_interval(days => current_tracking.required_days)
       and active_count >= current_tracking.minimum_active_testers
      then 'eligible_for_review'
      else current_tracking.tracking_status
    end,
    current_tracking.started_at,
    current_tracking.required_days,
    case when current_tracking.started_at is null then 0
         else greatest(0, floor(extract(epoch from (now() - current_tracking.started_at)) / 86400)::integer) end,
    active_count,
    current_tracking.minimum_active_testers;
end;
$$;
revoke all on function private.get_betalink_campaign_tracking(uuid) from public, anon;
grant execute on function private.get_betalink_campaign_tracking(uuid) to authenticated;

create or replace function public.get_betalink_campaign_tracking(p_campaign_id uuid)
returns table (
  tracking_status text,
  started_at timestamptz,
  required_days integer,
  elapsed_days integer,
  active_testers integer,
  required_testers integer
)
language sql
security invoker
set search_path = public, pg_temp
as $$
  select * from private.get_betalink_campaign_tracking(p_campaign_id);
$$;
revoke all on function public.get_betalink_campaign_tracking(uuid) from public, anon;
grant execute on function public.get_betalink_campaign_tracking(uuid) to authenticated;
