-- Betalink hardening: bind check-ins to their actual campaign and make attendance
-- processing idempotent per UTC day. No admin-role hierarchy is introduced.

alter table public.campaign_members
  add column if not exists last_absence_check_date date;

alter table public.group_members
  add column if not exists last_absence_check_date date;

drop policy if exists campaign_checkins_insert_self on public.campaign_daily_checkins;
create policy campaign_checkins_insert_self
on public.campaign_daily_checkins
for insert
to authenticated
with check (
  tester_id = (select auth.uid())
  and checkin_date = ((now() at time zone 'utc')::date)
  and exists (
    select 1 from public.campaign_members m
    where m.campaign_id = campaign_daily_checkins.campaign_id
      and m.tester_id = (select auth.uid())
      and m.status in ('accepted', 'opted_in', 'active')
      and not m.is_backup
  )
);

create or replace function public.betalink_process_campaign_attendance(p_campaign_id uuid)
returns jsonb language plpgsql security definer set search_path = ''
as $fn$
declare
  v_actor uuid := auth.uid();
  v_owner uuid;
  v_yesterday date := ((pg_catalog.now() at time zone 'utc')::date - 1);
  v_missing integer := 0;
  v_dropped integer := 0;
  v_promoted integer := 0;
  m record;
  b record;
begin
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_campaign_id::text, 0));
  select c.developer_id into v_owner from public.campaigns c where c.id = p_campaign_id;
  if v_owner is null then raise exception 'Kampanya bulunamadı.'; end if;
  if v_actor is not null and v_owner <> v_actor then raise exception 'Bu kampanyayı yönetme yetkin yok.'; end if;
  if v_actor is null
     and coalesce(pg_catalog.current_setting('request.jwt.claim.role', true), '') <> 'service_role'
     and session_user not in ('postgres', 'supabase_admin') then
    raise exception 'Oturum gerekli.';
  end if;

  for m in
    select cm.id, cm.tester_id, cm.missed_days, cm.last_absence_check_date,
      exists (
        select 1 from public.campaign_daily_checkins dc
        where dc.campaign_id = cm.campaign_id and dc.tester_id = cm.tester_id
          and dc.checkin_date = v_yesterday
      ) as checked_yesterday
    from public.campaign_members cm
    where cm.campaign_id = p_campaign_id
      and cm.status in ('active', 'opted_in', 'accepted') and not cm.is_backup
    for update
  loop
    if m.last_absence_check_date is distinct from v_yesterday then
      if not m.checked_yesterday then
        update public.campaign_members
        set missed_days = missed_days + 1,
            last_absence_notice_at = pg_catalog.now(),
            last_absence_check_date = v_yesterday,
            status = case when missed_days + 1 >= 2 then 'dropped' else status end
        where id = m.id;
        v_missing := v_missing + 1;
        insert into public.notifications(recipient_id, notification_type, title, body)
        values (m.tester_id, 'attendance', 'Günlük test bildirimi eksik',
          case when m.missed_days + 1 >= 2
            then 'İki gün üst üste test bildirimi gelmediği için aktif kontenjanın boşaltıldı.'
            else 'Dünkü test bildirimin henüz yok. Testini tamamlayıp günlük bildirimi gönder.' end);
        if m.missed_days + 1 >= 2 then
          v_dropped := v_dropped + 1;
          select * into b from public.campaign_members
          where campaign_id = p_campaign_id and is_backup
            and status in ('applied', 'waitlisted')
          order by joined_at asc limit 1 for update skip locked;
          if found then
            update public.campaign_members
            set status = 'active', is_backup = false, activated_at = pg_catalog.now(),
                missed_days = 0, last_absence_check_date = v_yesterday
            where id = b.id;
            insert into public.notifications(recipient_id, notification_type, title, body)
            values (b.tester_id, 'backup_promotion', 'Yedek tester olarak çağrıldın',
              'Boşalan kontenjan için aktif tester olarak seçildin.');
            v_promoted := v_promoted + 1;
          end if;
        end if;
      else
        update public.campaign_members set missed_days = 0, last_absence_check_date = v_yesterday
        where id = m.id;
      end if;
    end if;
  end loop;
  return pg_catalog.jsonb_build_object('checked', v_missing, 'dropped', v_dropped,
    'promoted', v_promoted, 'processed_date', v_yesterday);
end;
$fn$;

create or replace function public.betalink_process_all_group_attendance()
returns integer language plpgsql security definer set search_path = ''
as $fn$
declare
  g record;
  m record;
  b record;
  v_yesterday date := ((pg_catalog.now() at time zone 'utc')::date - 1);
  n integer := 0;
begin
  if coalesce(pg_catalog.current_setting('request.jwt.claim.role', true), '') <> 'service_role'
     and session_user not in ('postgres', 'supabase_admin') then
    raise exception 'Sistem yetkisi gerekli.';
  end if;
  for g in
    select tg.id from public.tester_groups tg
    where tg.tracking_status = 'running' order by tg.id for update
  loop
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(g.id::text, 1));
    for m in
      select gm.id, gm.user_id, gm.missed_days, gm.last_absence_check_date,
        exists (
          select 1 from public.group_daily_checkins dc
          where dc.group_id = gm.group_id and dc.user_id = gm.user_id
            and dc.checkin_date = v_yesterday
        ) as checked_yesterday
      from public.group_members gm
      where gm.group_id = g.id and gm.status = 'approved'
        and gm.member_role <> 'leader'
        and gm.last_absence_check_date is distinct from v_yesterday
      for update
    loop
      if not m.checked_yesterday then
        update public.group_members set missed_days = missed_days + 1,
          last_absence_check_date = v_yesterday where id = m.id;
        insert into public.notifications(recipient_id, notification_type, title, body)
        values (m.user_id, 'group_attendance', 'Grup günlük test bildirimin eksik',
          'Dünkü grup test bildirimin bulunmuyor. Devam etmek için testini tamamlayıp günlük bildirimi gönder.');
        if m.missed_days + 1 >= 2 then
          update public.group_members
          set status = 'removed', share_test_email = false, play_test_email = null
          where id = m.id;
          insert into public.notifications(recipient_id, notification_type, title, body)
          values (m.user_id, 'group_membership', 'Grup kontenjanın boşaltıldı',
            'İki gün üst üste günlük test bildirimi gelmediği için aktif grup kontenjanın yedek sıraya devredildi.');
          select * into b from public.group_members
          where group_id = g.id and status = 'pending'
          order by joined_at asc limit 1 for update skip locked;
          if found then
            update public.group_members set status = 'approved', missed_days = 0,
              last_absence_check_date = v_yesterday where id = b.id;
            insert into public.notifications(recipient_id, notification_type, title, body)
            values (b.user_id, 'group_backup_promotion', 'Grup yedek listesinden çağrıldın',
              'Boşalan grup kontenjanına alındın. Grup testine katılabilirsin.');
          end if;
        end if;
      else
        update public.group_members set missed_days = 0, last_absence_check_date = v_yesterday
        where id = m.id;
      end if;
      n := n + 1;
    end loop;
  end loop;
  return n;
end;
$fn$;

revoke all on function public.betalink_process_campaign_attendance(uuid) from public, anon;
grant execute on function public.betalink_process_campaign_attendance(uuid) to authenticated, service_role;
revoke all on function public.betalink_process_all_group_attendance() from public, anon, authenticated;
grant execute on function public.betalink_process_all_group_attendance() to service_role;
