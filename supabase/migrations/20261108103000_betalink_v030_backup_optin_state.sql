-- Promoted backup applicants must complete Play opt-in before attendance tracking starts.
create or replace function public.betalink_process_campaign_attendance(p_campaign_id uuid)
returns jsonb language plpgsql security definer set search_path=public,auth as $fn$
declare v_actor uuid:=auth.uid(); v_owner uuid; v_missing integer:=0; v_dropped integer:=0; v_promoted integer:=0; m record; b record;
begin
 select developer_id into v_owner from public.campaigns where id=p_campaign_id;
 if v_owner is null then raise exception 'Kampanya bulunamadı.'; end if;
 if v_actor is not null and v_owner<>v_actor then raise exception 'Bu kampanyayı yönetme yetkin yok.'; end if;
 if v_actor is null and current_user not in ('postgres','service_role','supabase_admin') then raise exception 'Oturum gerekli.'; end if;
 for m in
  select cm.id,cm.tester_id,cm.missed_days,
   exists(select 1 from public.campaign_daily_checkins dc where dc.campaign_id=cm.campaign_id and dc.tester_id=cm.tester_id and dc.checkin_date=((now() at time zone 'utc')::date-1)) checked_yesterday
  from public.campaign_members cm where cm.campaign_id=p_campaign_id and cm.status in ('active','opted_in') and not cm.is_backup
 loop
  if not m.checked_yesterday then
   update public.campaign_members set missed_days=missed_days+1,last_absence_notice_at=now(),status=case when missed_days+1>=2 then 'dropped' else status end where id=m.id;
   v_missing:=v_missing+1;
   insert into public.notifications(recipient_id,notification_type,title,body)
   values(m.tester_id,'attendance','Günlük test bildirimi eksik',case when m.missed_days+1>=2 then 'İki gün üst üste test bildirimi gelmediği için aktif kontenjanın boşaltıldı.' else 'Dünkü test bildirimin henüz yok. Testini tamamlayıp günlük bildirimi gönder.' end);
   if m.missed_days+1>=2 then
    v_dropped:=v_dropped+1;
    select * into b from public.campaign_members where campaign_id=p_campaign_id and is_backup and status in ('applied','waitlisted') order by joined_at asc limit 1 for update skip locked;
    if found then
     update public.campaign_members set status='accepted',is_backup=false,activated_at=now(),missed_days=0 where id=b.id;
     insert into public.notifications(recipient_id,notification_type,title,body)
     values(b.tester_id,'backup_promotion','Yedek tester olarak çağrıldın','Boşalan kontenjan için seçildin. Kampanyaya katılımını tamamlamak için Testlerim bölümünden Google Play test katılımını onayla.');
     v_promoted:=v_promoted+1;
    end if;
   end if;
  else update public.campaign_members set missed_days=0 where id=m.id and missed_days<>0;
  end if;
 end loop;
 return jsonb_build_object('checked',v_missing,'dropped',v_dropped,'promoted',v_promoted);
end $fn$;

create or replace view public.betalink_campaign_report with (security_invoker=true) as
select c.id campaign_id,c.developer_id,c.app_name,c.target_testers,c.status,
 (select count(*) from public.campaign_members cm where cm.campaign_id=c.id and cm.status in ('active','opted_in','completed'))::integer active_testers,
 (select count(*) from public.campaign_members cm where cm.campaign_id=c.id and cm.is_backup)::integer backup_testers,
 (select count(*) from public.campaign_members cm where cm.campaign_id=c.id and cm.status='dropped')::integer dropped_testers,
 (select count(*) from public.campaign_daily_checkins dc where dc.campaign_id=c.id)::integer total_checkins,
 (select count(*) from public.feedback f where f.campaign_id=c.id)::integer feedback_count,
 (select round(avg(f.rating)::numeric,2) from public.feedback f where f.campaign_id=c.id) average_rating,
 (select count(*) from public.feedback f where f.campaign_id=c.id and f.feedback_type='bug')::integer bug_reports,
 (select count(*) from public.feedback f where f.campaign_id=c.id and f.feedback_type='suggestion')::integer suggestions,
 (select count(*) from public.campaign_members cm where cm.campaign_id=c.id and cm.status='accepted')::integer accepted_testers
from public.campaigns c;