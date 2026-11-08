create or replace view public.betalink_tester_reliability with (security_invoker=true) as
select p.user_id,p.display_name,
 (select count(*) from public.campaign_members cm where cm.tester_id=p.user_id and cm.status in ('completed','active','opted_in','accepted','dropped'))::integer tests_joined,
 (select count(*) from public.campaign_members cm where cm.tester_id=p.user_id and cm.status='completed')::integer tests_completed,
 (select count(*) from public.campaign_members cm where cm.tester_id=p.user_id and cm.status='dropped')::integer tests_dropped,
 ((select count(*) from public.campaign_daily_checkins dc where dc.tester_id=p.user_id)+(select count(*) from public.group_daily_checkins gd where gd.user_id=p.user_id))::integer daily_checkins,
 (select coalesce(sum(cm.missed_days),0) from public.campaign_members cm where cm.tester_id=p.user_id)
  +(select coalesce(sum(gm.missed_days),0) from public.group_members gm where gm.user_id=p.user_id)::integer missed_days,
 (select coalesce(sum(re.points),0) from public.reputation_events re where re.user_id=p.user_id)::integer reputation_points
from public.profiles p;