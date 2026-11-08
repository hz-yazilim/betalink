create or replace function public.betalink_tester_reliability_for_campaign(p_campaign_id uuid,p_tester_id uuid)
returns jsonb language plpgsql security definer set search_path=public,auth as $fn$
declare actor uuid:=auth.uid(); owner_id uuid; result jsonb;
begin
 if actor is null then raise exception 'Oturum gerekli.'; end if;
 select developer_id into owner_id from public.campaigns where id=p_campaign_id;
 if owner_id is distinct from actor then raise exception 'Bu kampanyayı görüntüleme yetkin yok.'; end if;
 if not exists(select 1 from public.campaign_members where campaign_id=p_campaign_id and tester_id=p_tester_id) then
  raise exception 'Tester bu kampanyaya başvurmamış.';
 end if;
 select jsonb_build_object(
  'tests_joined',(select count(*) from public.campaign_members where tester_id=p_tester_id and status in ('completed','active','opted_in','accepted','dropped')),
  'tests_completed',(select count(*) from public.campaign_members where tester_id=p_tester_id and status='completed'),
  'tests_dropped',(select count(*) from public.campaign_members where tester_id=p_tester_id and status='dropped'),
  'daily_checkins',(select count(*) from public.campaign_daily_checkins where tester_id=p_tester_id)+(select count(*) from public.group_daily_checkins where user_id=p_tester_id),
  'missed_days',(select coalesce(sum(missed_days),0) from public.campaign_members where tester_id=p_tester_id)+(select coalesce(sum(missed_days),0) from public.group_members where user_id=p_tester_id),
  'reputation_points',(select coalesce(sum(points),0) from public.reputation_events where user_id=p_tester_id)
 ) into result;
 return result;
end $fn$;
revoke all on function public.betalink_tester_reliability_for_campaign(uuid,uuid) from public,anon;
grant execute on function public.betalink_tester_reliability_for_campaign(uuid,uuid) to authenticated;