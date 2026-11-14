grant insert, update on table public.profile_contact_settings to authenticated;
grant insert on table public.blocked_users to authenticated;

create or replace function public.betalink_public_user_profile(p_user_id uuid)
returns table (
  user_id uuid,
  display_name text,
  bio text,
  avatar_url text,
  reputation_points integer,
  beta_points integer,
  tests_joined integer,
  tests_completed integer,
  tests_dropped integer,
  daily_checkins integer,
  missed_days integer,
  attendance_percent integer,
  same_group boolean
)
language sql
stable
security definer
set search_path = ''
as $function$
  select
    p.user_id,
    p.display_name,
    p.bio,
    p.avatar_url,
    coalesce((
      select sum(re.points)::integer
      from public.reputation_events re
      where re.user_id = p.user_id
    ), 0) as reputation_points,
    coalesce((
      select bw.balance
      from public.betalink_wallets bw
      where bw.user_id = p.user_id
    ), 0) as beta_points,
    (
      select count(*)::integer
      from public.campaign_members cm
      where cm.tester_id = p.user_id
        and cm.status in ('accepted', 'opted_in', 'active', 'completed', 'dropped')
    ) + (
      select count(*)::integer
      from public.group_members gm
      where gm.user_id = p.user_id
        and gm.status in ('approved', 'accepted', 'active', 'completed')
    ) as tests_joined,
    (
      select count(*)::integer
      from public.campaign_members cm
      where cm.tester_id = p.user_id and cm.status = 'completed'
    ) + (
      select count(*)::integer
      from public.group_members gm
      join public.tester_groups tg on tg.id = gm.group_id
      where gm.user_id = p.user_id
        and gm.status in ('approved', 'accepted', 'active', 'completed')
        and tg.tracking_status = 'completed'
    ) as tests_completed,
    (
      select count(*)::integer
      from public.campaign_members cm
      where cm.tester_id = p.user_id and cm.status = 'dropped'
    ) + (
      select count(*)::integer
      from public.group_members gm
      where gm.user_id = p.user_id and gm.status in ('dropped', 'removed')
    ) as tests_dropped,
    (
      select count(*)::integer
      from public.campaign_daily_checkins dc
      where dc.tester_id = p.user_id
    ) + (
      select count(*)::integer
      from public.group_daily_checkins gd
      where gd.user_id = p.user_id
    ) as daily_checkins,
    coalesce((
      select sum(cm.missed_days)::integer
      from public.campaign_members cm
      where cm.tester_id = p.user_id
    ), 0) + coalesce((
      select sum(gm.missed_days)::integer
      from public.group_members gm
      where gm.user_id = p.user_id
    ), 0) as missed_days,
    case
      when (
        (
          select count(*)::integer
          from public.campaign_daily_checkins dc
          where dc.tester_id = p.user_id
        ) + (
          select count(*)::integer
          from public.group_daily_checkins gd
          where gd.user_id = p.user_id
        ) + coalesce((
          select sum(cm.missed_days)::integer
          from public.campaign_members cm
          where cm.tester_id = p.user_id
        ), 0) + coalesce((
          select sum(gm.missed_days)::integer
          from public.group_members gm
          where gm.user_id = p.user_id
        ), 0)
      ) = 0 then 100
      else round(
        100.0 * (
          (
            select count(*)::integer
            from public.campaign_daily_checkins dc
            where dc.tester_id = p.user_id
          ) + (
            select count(*)::integer
            from public.group_daily_checkins gd
            where gd.user_id = p.user_id
          )
        )::numeric / greatest(1, (
          (
            select count(*)::integer
            from public.campaign_daily_checkins dc
            where dc.tester_id = p.user_id
          ) + (
            select count(*)::integer
            from public.group_daily_checkins gd
            where gd.user_id = p.user_id
          ) + coalesce((
            select sum(cm.missed_days)::integer
            from public.campaign_members cm
            where cm.tester_id = p.user_id
          ), 0) + coalesce((
            select sum(gm.missed_days)::integer
            from public.group_members gm
            where gm.user_id = p.user_id
          ), 0)
        ))
      )::integer
    end as attendance_percent,
    exists (
      select 1
      from public.group_members mine
      join public.group_members other_member on other_member.group_id = mine.group_id
      where mine.user_id = (select auth.uid())
        and other_member.user_id = p.user_id
        and mine.status in ('approved', 'accepted', 'active', 'completed')
        and other_member.status in ('approved', 'accepted', 'active', 'completed')
    ) as same_group
  from public.profiles p
  where p.user_id = p_user_id
    and (select auth.uid()) is not null
    and p.user_id <> (select auth.uid())
    and not exists (
      select 1
      from public.blocked_users b
      where (b.blocker_id = (select auth.uid()) and b.blocked_id = p.user_id)
         or (b.blocker_id = p.user_id and b.blocked_id = (select auth.uid()))
    );
$function$;

revoke all on function public.betalink_public_user_profile(uuid) from public;
revoke all on function public.betalink_public_user_profile(uuid) from anon;
grant execute on function public.betalink_public_user_profile(uuid) to authenticated;
