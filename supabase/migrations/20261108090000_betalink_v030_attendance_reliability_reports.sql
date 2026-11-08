-- Betalink 0.3.0 backend: attendance automation, backup promotion, moderation, reliability and reports.
alter table public.campaign_members
  add column if not exists missed_days integer not null default 0,
  add column if not exists last_absence_notice_at timestamptz,
  add column if not exists activated_at timestamptz,
  add column if not exists completed_at timestamptz;

create index if not exists campaign_members_backup_queue_idx
  on public.campaign_members(campaign_id, joined_at)
  where is_backup = true and status in ('applied','waitlisted');
create index if not exists campaign_daily_checkins_member_date_idx
  on public.campaign_daily_checkins(campaign_id, tester_id, checkin_date desc);

create or replace function public.betalink_process_campaign_attendance(p_campaign_id uuid)
returns jsonb language plpgsql security definer set search_path=public,auth as $fn$
declare
  v_actor uuid := auth.uid(); v_owner uuid; v_missing integer:=0; v_dropped integer:=0; v_promoted integer:=0;
  m record; b record;
begin
  select developer_id into v_owner from public.campaigns where id=p_campaign_id;
  if v_owner is null then raise exception 'Kampanya bulunamadı.'; end if;
  if v_actor is not null and v_owner <> v_actor then raise exception 'Bu kampanyayı yönetme yetkin yok.'; end if;
  if v_actor is null and current_user not in ('postgres','service_role','supabase_admin') then raise exception 'Oturum gerekli.'; end if;
  for m in
    select cm.id,cm.tester_id,cm.missed_days,
      coalesce(max(dc.checkin_date),(cm.joined_at at time zone 'utc')::date - 1) last_checkin
    from public.campaign_members cm
    left join public.campaign_daily_checkins dc on dc.campaign_id=cm.campaign_id and dc.tester_id=cm.tester_id
    where cm.campaign_id=p_campaign_id and cm.status in ('active','opted_in','accepted') and not cm.is_backup
    group by cm.id
  loop
    if m.last_checkin < (now() at time zone 'utc')::date then
      update public.campaign_members set missed_days=missed_days+1,last_absence_notice_at=now(),
        status=case when missed_days+1>=2 then 'dropped' else status end where id=m.id;
      v_missing:=v_missing+1;
      insert into public.notifications(recipient_id,notification_type,title,body)
      values(m.tester_id,'attendance','Günlük test bildirimi eksik',
        case when m.missed_days+1>=2 then 'İki gün üst üste test bildirimi gelmediği için aktif kontenjanın boşaltıldı.'
        else 'Bugünkü test bildirimini henüz yapmadın. Katılımını korumak için testini tamamlayıp bildir.' end);
      if m.missed_days+1>=2 then
        v_dropped:=v_dropped+1;
        select * into b from public.campaign_members where campaign_id=p_campaign_id
          and is_backup and status in ('applied','waitlisted') order by joined_at asc limit 1 for update skip locked;
        if found then
          update public.campaign_members set status='active',is_backup=false,activated_at=now(),missed_days=0 where id=b.id;
          insert into public.notifications(recipient_id,notification_type,title,body)
          values(b.tester_id,'backup_promotion','Yedek tester olarak çağrıldın','Boşalan kontenjan için aktif tester olarak seçildin.');
          v_promoted:=v_promoted+1;
        end if;
      end if;
    else
      update public.campaign_members set missed_days=0 where id=m.id and missed_days<>0;
    end if;
  end loop;
  return jsonb_build_object('checked',v_missing,'dropped',v_dropped,'promoted',v_promoted);
end $fn$;
revoke all on function public.betalink_process_campaign_attendance(uuid) from public,anon;
grant execute on function public.betalink_process_campaign_attendance(uuid) to authenticated;

create or replace function public.betalink_process_all_campaign_attendance()
returns integer language plpgsql security definer set search_path=public as $fn$
declare c record; n integer:=0;
begin
  if current_user not in ('postgres','service_role','supabase_admin') then raise exception 'Sistem yetkisi gerekli.'; end if;
  for c in select id from public.campaigns where status in ('open','paused') loop
    perform public.betalink_process_campaign_attendance(c.id); n:=n+1;
  end loop;
  return n;
end $fn$;
revoke all on function public.betalink_process_all_campaign_attendance() from public,anon,authenticated;

create or replace function public.betalink_group_remove_member(p_group_id uuid,p_member_id uuid)
returns boolean language plpgsql security definer set search_path=public,auth as $fn$
declare actor uuid:=auth.uid(); owner_id uuid; removed_user uuid; promoted_user uuid;
begin
  if actor is null then raise exception 'Oturum gerekli.'; end if;
  select created_by into owner_id from public.tester_groups where id=p_group_id;
  if owner_id is distinct from actor then raise exception 'Yalnızca grup kurucusu üye çıkarabilir.'; end if;
  update public.group_members set status='removed',share_test_email=false,play_test_email=null
    where id=p_member_id and group_id=p_group_id and status='approved' returning user_id into removed_user;
  if removed_user is null then raise exception 'Aktif üye bulunamadı.'; end if;
  insert into public.notifications(recipient_id,notification_type,title,body)
  values(removed_user,'group_membership','Grup üyeliğin sona erdi','Grup yöneticisi üyeliğini sonlandırdı.');
  with next_member as (
    select id,user_id from public.group_members where group_id=p_group_id and status='pending'
    order by joined_at asc limit 1 for update skip locked
  )
  update public.group_members gm set status='approved'
    from next_member nm where gm.id=nm.id returning gm.user_id into promoted_user;
  if promoted_user is not null then
    insert into public.notifications(recipient_id,notification_type,title,body)
    values(promoted_user,'group_membership','Grup üyeliğin onaylandı','Boşalan grup kontenjanına yedek sıradan alındın.');
  end if;
  return true;
end $fn$;
revoke all on function public.betalink_group_remove_member(uuid,uuid) from public,anon;
grant execute on function public.betalink_group_remove_member(uuid,uuid) to authenticated;

create or replace view public.betalink_tester_reliability with (security_invoker=true) as
select p.user_id,p.display_name,
 (select count(*) from public.campaign_members cm where cm.tester_id=p.user_id and cm.status in ('completed','active','opted_in','accepted','dropped'))::integer tests_joined,
 (select count(*) from public.campaign_members cm where cm.tester_id=p.user_id and cm.status='completed')::integer tests_completed,
 (select count(*) from public.campaign_members cm where cm.tester_id=p.user_id and cm.status='dropped')::integer tests_dropped,
 (select count(*) from public.campaign_daily_checkins dc where dc.tester_id=p.user_id)::integer daily_checkins,
 (select coalesce(sum(re.points),0) from public.reputation_events re where re.user_id=p.user_id)::integer reputation_points
from public.profiles p;

create or replace view public.betalink_campaign_report with (security_invoker=true) as
select c.id campaign_id,c.developer_id,c.app_name,c.target_testers,c.status,
 (select count(*) from public.campaign_members cm where cm.campaign_id=c.id and cm.status in ('active','opted_in','accepted','completed'))::integer active_testers,
 (select count(*) from public.campaign_members cm where cm.campaign_id=c.id and cm.is_backup)::integer backup_testers,
 (select count(*) from public.campaign_members cm where cm.campaign_id=c.id and cm.status='dropped')::integer dropped_testers,
 (select count(*) from public.campaign_daily_checkins dc where dc.campaign_id=c.id)::integer total_checkins,
 (select count(*) from public.feedback f where f.campaign_id=c.id)::integer feedback_count,
 (select round(avg(f.rating)::numeric,2) from public.feedback f where f.campaign_id=c.id) average_rating,
 (select count(*) from public.feedback f where f.campaign_id=c.id and f.feedback_type='bug')::integer bug_reports,
 (select count(*) from public.feedback f where f.campaign_id=c.id and f.feedback_type='suggestion')::integer suggestions
from public.campaigns c;

create or replace function public.betalink_campaign_feedback_summary(p_campaign_id uuid)
returns jsonb language plpgsql security definer set search_path=public,auth as $fn$
declare actor uuid:=auth.uid(); owner_id uuid; result jsonb;
begin
  if actor is null then raise exception 'Oturum gerekli.'; end if;
  select developer_id into owner_id from public.campaigns where id=p_campaign_id;
  if owner_id is distinct from actor then raise exception 'Bu raporu görüntüleme yetkin yok.'; end if;
  select jsonb_build_object('feedback_count',count(*),'average_rating',round(avg(rating)::numeric,2),
    'bugs',count(*) filter(where feedback_type='bug'),'experiences',count(*) filter(where feedback_type='experience'),
    'suggestions',count(*) filter(where feedback_type='suggestion'),'resolved',count(*) filter(where status='resolved'),
    'highlights',coalesce(jsonb_agg(jsonb_build_object('type',feedback_type,'rating',rating,'body',left(body,500),'created_at',created_at) order by created_at desc),'[]'::jsonb))
  into result from public.feedback where campaign_id=p_campaign_id;
  return result;
end $fn$;
revoke all on function public.betalink_campaign_feedback_summary(uuid) from public,anon;
grant execute on function public.betalink_campaign_feedback_summary(uuid) to authenticated;

do $cron$
begin
 if exists(select 1 from pg_extension where extname='pg_cron') then
   execute 'select cron.unschedule(jobid) from cron.job where jobname = ''betalink-daily-attendance''';
   execute 'select cron.schedule(''betalink-daily-attendance'',''15 9 * * *'',''select public.betalink_process_all_campaign_attendance();'')';
 end if;
exception when undefined_table or undefined_function then
  raise notice 'pg_cron unavailable; daily attendance processor must be scheduled when pg_cron is enabled.';
end
$cron$;
