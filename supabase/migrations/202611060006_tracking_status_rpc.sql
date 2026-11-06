-- Expose campaign clock only to accepted participants and the campaign owner.
-- A 14-day result is an eligibility signal, not proof of Google Play Console compliance.
create or replace function public.get_betalink_campaign_tracking(p_campaign_id uuid)
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
  where campaign_id = p_campaign_id
  for update;

  if not found then
    raise exception 'Tracking record not found' using errcode = 'P0002';
  end if;

  select count(*)::integer into active_count
  from public.campaign_members
  where campaign_id = p_campaign_id
    and status in ('opted_in','active')
    and opted_in_at is not null;

  if current_tracking.tracking_status = 'running'
     and current_tracking.started_at <= now() - make_interval(days => current_tracking.required_days)
     and active_count >= current_tracking.minimum_active_testers then
    update public.campaign_tracking
       set tracking_status = 'eligible_for_review', updated_at = now()
     where campaign_id = p_campaign_id;
    current_tracking.tracking_status := 'eligible_for_review';
  end if;

  return query select
    current_tracking.tracking_status,
    current_tracking.started_at,
    current_tracking.required_days,
    case when current_tracking.started_at is null then 0
         else greatest(0, floor(extract(epoch from (now() - current_tracking.started_at)) / 86400)::integer) end,
    active_count,
    current_tracking.minimum_active_testers;
end;
$$;

revoke all on function public.get_betalink_campaign_tracking(uuid) from public, anon;
grant execute on function public.get_betalink_campaign_tracking(uuid) to authenticated;
