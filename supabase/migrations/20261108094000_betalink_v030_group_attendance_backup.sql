alter table public.group_members add column if not exists missed_days integer not null default 0;
create index if not exists group_members_pending_queue_idx on public.group_members(group_id,joined_at) where status='pending';

create or replace function public.betalink_process_all_group_attendance()
returns integer language plpgsql security definer set search_path=public as $fn$
declare g record; m record; b record; n integer:=0;
begin
 if current_user not in ('postgres','service_role','supabase_admin') then raise exception 'Sistem yetkisi gerekli.'; end if;
 for g in select tg.id,tg.created_by from public.tester_groups tg where tg.tracking_status='running' loop
  for m in
   select gm.id,gm.user_id,gm.missed_days,
    coalesce(max(dc.checkin_date),(gm.joined_at at time zone 'utc')::date-1) last_checkin
   from public.group_members gm
   left join public.group_daily_checkins dc on dc.group_id=gm.group_id and dc.user_id=gm.user_id
   where gm.group_id=g.id and gm.status='approved' and gm.user_id<>g.created_by
   group by gm.id
  loop
   if m.last_checkin < ((now() at time zone 'utc')::date-1) then
    update public.group_members set missed_days=missed_days+1 where id=m.id;
    insert into public.notifications(recipient_id,notification_type,title,body)
    values(m.user_id,'group_attendance','Grup günlük test bildirimin eksik','Dünkü grup test bildirimin bulunmuyor. Devam etmek için testini tamamlayıp günlük bildirimi gönder.');
    if m.missed_days+1>=2 then
     update public.group_members set status='removed',share_test_email=false,play_test_email=null where id=m.id;
     insert into public.notifications(recipient_id,notification_type,title,body)
     values(m.user_id,'group_membership','Grup kontenjanın boşaltıldı','İki gün üst üste günlük test bildirimi gelmediği için aktif grup kontenjanın yedek sıraya devredildi.');
     select * into b from public.group_members where group_id=g.id and status='pending'
      order by joined_at asc limit 1 for update skip locked;
     if found then
      update public.group_members set status='approved',missed_days=0 where id=b.id;
      insert into public.notifications(recipient_id,notification_type,title,body)
      values(b.user_id,'group_backup_promotion','Grup yedek listesinden çağrıldın','Boşalan grup kontenjanına alındın. Grup testine katılabilirsin.');
     end if;
    end if;
   else
    update public.group_members set missed_days=0 where id=m.id and missed_days<>0;
   end if;
   n:=n+1;
  end loop;
 end loop;
 return n;
end $fn$;
revoke all on function public.betalink_process_all_group_attendance() from public,anon,authenticated;

do $cron$
begin
 if exists(select 1 from pg_extension where extname='pg_cron') then
  execute 'select cron.unschedule(jobid) from cron.job where jobname = ''betalink-daily-attendance''';
  execute 'select cron.schedule(''betalink-daily-attendance'',''15 9 * * *'',''select public.betalink_process_all_campaign_attendance(); select public.betalink_process_all_group_attendance();'')';
 end if;
exception when undefined_table or undefined_function then
 raise notice 'pg_cron unavailable; daily attendance processing must be scheduled when pg_cron is enabled.';
end
$cron$;