-- Betalink 0.3.0: attendance, backup promotion, group moderation, reliability and reports.
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
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_actor uuid := auth.uid();
  v_developer uuid;
  v_missing integer := 0;
  v_dropped integer := 0;
  v_promoted integer := 0;
  v_member record;
  v_backup record;
begin
  if v_actor is null then raise exception 'Oturum gerekli.'; end if;
  select developer_id into v_developer from public.campaigns where id = p_campaign_id;
  if v_developer is null or v_developer <> v_actor then raise exception 'Bu kampanyayı yönetme yetkin yok.'; end if;

  for v_member in
    select cm.id, cm.tester_id, cm.missed_days,
           coalesce(max(dc.checkin_date), (cm.joined_at at time zone 'utc')::date - 1) as last_checkin
    from public.campaign_members cm
    left join public.campaign_daily_checkins dc
      on dc.campaign_id = cm.campaign_id and dc.tester_id = cm.tester_id
    where cm.campaign_id = p_campaign_id
      and cm.status in ('active','opted_in','accepted')
      and cm.is_backup = false
    group by cm.id
  loop
    if v_member.last_checkin < (now() at time zone 'utc')::date then
      update public.campaign_members
        set missed_days = missed_days + 1,
            last_absence_notice_at = now(),
            status = case when missed_days + 1 >= 2 then 'dropped' else status end
        where id = v_member.id;
      v_missing := v_missing + 1;
      insert into public.notifications(recipient_id, notification_type, title, body)
      values(v_member.tester_id, 'attendance', 'Günlük test bildirimi eksik',
        case when v_member.missed_days + 1 >= 2
          then 'İki gün üst üste test bildirimi gelmediği için kampanyadaki aktif yerin boşaltıldı.'
          else 'Bugünkü test bildirimini henüz yapmadın. Katılımını korumak için uygulamayı test edip bildir.' end);
      if v_member.missed_days + 1 >= 2 then
        v_dropped := v_dropped + 1;
        select * into v_backup from public.campaign_members
          where campaign_id = p_campaign_id and is_backup = true
            and status in ('applied','waitlisted')
          order by joined_at asc limit 1 for update skip locked;
        if found then
          update public.campaign_members
            set status = 'active', is_backup = false, activated_at = now(), missed_days = 0
            where id = v_backup.id;
          insert into public.notifications(recipient_id, notification_type, title, body)
          values(v_backup.tester_id, 'backup_promotion', 'Yedek tester olarak çağrıldın',
            'Bir kontenjan boşaldı. Kampanyadaki aktif tester olarak davet edildin.');
          v_promoted := v_promoted + 1;
        end if;
      end if;
    else
      update public.campaign_members set missed_days = 0 where id = v_member.id and missed_days <> 0;
    end if;
  end loop;
  return jsonb_build_object('checked',v_missing,'dropped',v_dropped,'promoted',v_promoted);
end;
$$;
revoke all on function public.betalink_process_campaign_attendance(uuid) from public, anon;
grant execute on function public.betalink_process_campaign_attendance(uuid) to authenticated;

create or replace function public.betalink_group_remove_member(p_group_id uuid, p_member_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public, auth
as $$
declare v_actor uuid := auth.uid(); v_owner uuid; v_user uuid;
begin
  if v_actor is null then raise exception 'Oturum gerekli.'; end if;
  select created_by into v_owner from public.tester_groups where id=p_group_id;
  if v_owner is distinct from v_actor then raise exception 'Yalnızca grup kurucusu üye çıkarabilir.'; end if;
  update public.group_members set status='removed', share_test_email=false, play_test_email=null
    where id=p_member_id and group_id=p_group_id and status='approved'
    returning user_id into v_user;
  if v_user is null then raise exception 'Aktif üye bulunamadı.'; end if;
  insert into public.notifications(recipient_id, notification_type, title, body)
  values(v_user,'group_membership','Grup üyeliğin sona erdi','Grup yöneticisi üyeliğini sonlandırdı.');
  -- Promote the oldest pending member to fill the released seat.
  with next_member as (
    select id,user_id from public.group_members
    where group_id=p_group_id and status='pending'
    order by joined_at asc limit 1 for update skip locked
  )
  update public.group_members gm set status='approved'
    from next_member nm where gm.id=nm.id;
  return true;
end;
$$;
revoke all on function public.betalink_group_remove_member(uuid,uuid) from public, anon;
grant execute on function public.betalink_group_remove_member(uuid,uuid) to authenticated;

create or replace view public.betalink_tester_reliability
with (security_invoker=true) as
select p.user_id,
       p.display_name,
       count(distinct cm.id) filter (where cm.status in ('completed','active','opted_in','accepted','dropped'))::integer as tests_joined,
       count(distinct cm.id) filter (where cm.status='completed')::integer as tests_completed,
       count(distinct cm.id) filter (where cm.status='dropped')::integer as tests_dropped,
       count(distinct dc.id)::integer as daily_checkins,
       coalesce(round(100.0 * count(distinct dc.id) /
         nullif(sum(greatest(coalesce(cm.missed_days,0)+1,1)) filter (where cm.status in ('completed','active','opted_in','accepted','dropped')),0)),0)::integer as attendance_percent,
       coalesce(sum(re.points),0)::integer as reputation_points
from public.profiles p
left join public.campaign_members cm on cm.tester_id=p.user_id
left join public.campaign_daily_checkins dc on dc.tester_id=p.user_id
left join public.reputation_events re on re.user_id=p.user_id
group by p.user_id,p.display_name;

create or replace view public.betalink_campaign_report
with (security_invoker=true) as
select c.id as campaign_id,c.developer_id,c.app_name,c.target_testers,c.status,
       count(distinct cm.id) filter (where cm.status in ('active','opted_in','accepted','completed'))::integer as active_testers,
       count(distinct cm.id) filter (where cm.is_backup)::integer as backup_testers,
       count(distinct cm.id) filter (where cm.status='dropped')::integer as dropped_testers,
       count(distinct dc.id)::integer as total_checkins,
       count(distinct f.id)::integer as feedback_count,
       round(avg(f.rating)::numeric,2) as average_rating,
       count(distinct f.id) filter (where f.feedback_type='bug')::integer as bug_reports,
       count(distinct f.id) filter (where f.feedback_type='suggestion')::integer as suggestions
from public.campaigns c
left join public.campaign_members cm on cm.campaign_id=c.id
left join public.campaign_daily_checkins dc on dc.campaign_id=c.id
left join public.feedback f on f.campaign_id=c.id
group by c.id,c.developer_id,c.app_name,c.target_testers,c.status;

create or replace function public.betalink_campaign_feedback_summary(p_campaign_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare v_actor uuid := auth.uid(); v_owner uuid; v_result jsonb;
begin
  if v_actor is null then raise exception 'Oturum gerekli.'; end if;
  select developer_id into v_owner from public.campaigns where id=p_campaign_id;
  if v_owner is distinct from v_actor then raise exception 'Bu raporu görüntüleme yetkin yok.'; end if;
  select jsonb_build_object(
    'feedback_count',count(*),
    'average_rating',round(avg(rating)::numeric,2),
    'bugs',count(*) filter(where feedback_type='bug'),
    'experiences',count(*) filter(where feedback_type='experience'),
    'suggestions',count(*) filter(where feedback_type='suggestion'),
    'resolved',count(*) filter(where status='resolved'),
    'highlights',coalesce(jsonb_agg(jsonb_build_object('type',feedback_type,'rating',rating,'body',left(body,500),'created_at',created_at) order by created_at desc),'[]'::jsonb)
  ) into v_result from public.feedback where campaign_id=p_campaign_id;
  return v_result;
end;
$$;
revoke all on function public.betalink_campaign_feedback_summary(uuid) from public, anon;
grant execute on function public.betalink_campaign_feedback_summary(uuid) to authenticated;

-- Daily server-side absence processing. No FCM dependency: notifications are written to the in-app inbox.
do $$
begin
  if exists(select 1 from pg_extension where extname='pg_cron') then
    perform cron.unschedule(jobid) from cron.job where jobname='betalink-daily-attendance';
    perform cron.schedule('betalink-daily-attendance','15 9 * * *',
      $$select public.betalink_process_all_campaign_attendance();$$);
  end if;
end $$;

create or replace function public.betalink_process_all_campaign_attendance()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare r record; n integer:=0;
begin
  for r in select id from public.campaigns where status in ('open','paused') loop
    perform public.betalink_process_campaign_attendance_as_system(r.id);
    n:=n+1;
  end loop;
  return n;
end;
$$;
