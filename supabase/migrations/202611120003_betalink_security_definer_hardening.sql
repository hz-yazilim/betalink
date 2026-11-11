create or replace function public.betalink_campaign_feedback_summary(p_campaign_id uuid)
returns jsonb language plpgsql security definer set search_path = ''
as $fn$
declare actor uuid := auth.uid(); owner_id uuid; result jsonb;
begin
  if actor is null then raise exception 'Oturum gerekli.'; end if;
  select c.developer_id into owner_id from public.campaigns c where c.id = p_campaign_id;
  if owner_id is distinct from actor then raise exception 'Bu raporu görüntüleme yetkin yok.'; end if;
  select pg_catalog.jsonb_build_object(
    'feedback_count', pg_catalog.count(*),
    'average_rating', pg_catalog.round(pg_catalog.avg(f.rating)::numeric,2),
    'bugs', pg_catalog.count(*) filter (where f.feedback_type='bug'),
    'experiences', pg_catalog.count(*) filter (where f.feedback_type='experience'),
    'suggestions', pg_catalog.count(*) filter (where f.feedback_type='suggestion'),
    'resolved', pg_catalog.count(*) filter (where f.status='resolved'),
    'highlights', pg_catalog.coalesce(
      pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object(
        'type',f.feedback_type,'rating',f.rating,'body',pg_catalog.left(f.body,500),'created_at',f.created_at
      ) order by f.created_at desc),'[]'::jsonb)
  ) into result
  from public.feedback f where f.campaign_id = p_campaign_id;
  return result;
end;
$fn$;

create or replace function public.betalink_group_daily_check_in(
  p_group_id uuid, p_note text default 'Uygulama içi günlük grup testi bildirimi'
) returns boolean language plpgsql security definer set search_path = ''
as $fn$
declare inserted_count integer;
begin
  if auth.uid() is null then raise exception 'Oturum gerekli.'; end if;
  if p_note is not null and pg_catalog.char_length(p_note) > 240 then raise exception 'Not en fazla 240 karakter olabilir.'; end if;
  if not exists (select 1 from public.group_members gm where gm.group_id=p_group_id and gm.user_id=auth.uid() and gm.status='approved') then
    raise exception 'Bu grubun onaylı üyesi değilsin.';
  end if;
  insert into public.group_daily_checkins(group_id,user_id,note)
  values(p_group_id,auth.uid(),coalesce(nullif(pg_catalog.btrim(p_note),''),'Günlük test bildirimi'))
  on conflict(group_id,user_id,checkin_date) do nothing;
  get diagnostics inserted_count = row_count;
  return inserted_count = 1;
end;
$fn$;

create or replace function public.betalink_group_remove_member(p_group_id uuid,p_member_id uuid)
returns boolean language plpgsql security definer set search_path = ''
as $fn$
declare actor uuid:=auth.uid(); owner_id uuid; removed_user uuid; promoted_user uuid;
begin
  if actor is null then raise exception 'Oturum gerekli.'; end if;
  select tg.created_by into owner_id from public.tester_groups tg where tg.id=p_group_id;
  if owner_id is distinct from actor then raise exception 'Yalnızca grup kurucusu üye çıkarabilir.'; end if;
  update public.group_members set status='removed',share_test_email=false,play_test_email=null
  where id=p_member_id and group_id=p_group_id and status='approved'
    and member_role <> 'leader' and user_id <> actor
  returning user_id into removed_user;
  if removed_user is null then raise exception 'Çıkarılabilecek aktif üye bulunamadı.'; end if;
  insert into public.notifications(recipient_id,notification_type,title,body)
  values(removed_user,'group_membership','Grup üyeliğin sona erdi','Grup yöneticisi üyeliğini sonlandırdı.');
  with next_member as (
    select gm.id,gm.user_id from public.group_members gm
    where gm.group_id=p_group_id and gm.status='pending'
    order by gm.joined_at asc limit 1 for update skip locked
  )
  update public.group_members gm set status='approved',missed_days=0
  from next_member nm where gm.id=nm.id returning gm.user_id into promoted_user;
  if promoted_user is not null then
    insert into public.notifications(recipient_id,notification_type,title,body)
    values(promoted_user,'group_membership','Grup üyeliğin onaylandı','Boşalan grup kontenjanına yedek sıradan alındın.');
  end if;
  return true;
end;
$fn$;

create or replace function public.betalink_tester_reliability_for_campaign(p_campaign_id uuid,p_tester_id uuid)
returns jsonb language plpgsql security definer set search_path = ''
as $fn$
declare actor uuid:=auth.uid(); owner_id uuid; result jsonb;
begin
  if actor is null then raise exception 'Oturum gerekli.'; end if;
  select c.developer_id into owner_id from public.campaigns c where c.id=p_campaign_id;
  if owner_id is distinct from actor then raise exception 'Bu kampanyayı görüntüleme yetkin yok.'; end if;
  if not exists(select 1 from public.campaign_members cm where cm.campaign_id=p_campaign_id and cm.tester_id=p_tester_id) then
    raise exception 'Tester bu kampanyaya başvurmamış.';
  end if;
  select pg_catalog.jsonb_build_object(
    'tests_joined',1,
    'tests_completed',case when cm.status='completed' then 1 else 0 end,
    'tests_dropped',case when cm.status='dropped' then 1 else 0 end,
    'daily_checkins',(select pg_catalog.count(*) from public.campaign_daily_checkins dc where dc.campaign_id=p_campaign_id and dc.tester_id=p_tester_id),
    'missed_days',cm.missed_days,
    'reputation_points',(select pg_catalog.coalesce(pg_catalog.sum(re.points),0) from public.reputation_events re where re.user_id=p_tester_id)
  ) into result from public.campaign_members cm where cm.campaign_id=p_campaign_id and cm.tester_id=p_tester_id;
  return result;
end;
$fn$;

revoke all on function public.betalink_campaign_feedback_summary(uuid) from public,anon;
revoke all on function public.betalink_group_daily_check_in(uuid,text) from public,anon;
revoke all on function public.betalink_group_remove_member(uuid,uuid) from public,anon;
revoke all on function public.betalink_tester_reliability_for_campaign(uuid,uuid) from public,anon;
grant execute on function public.betalink_campaign_feedback_summary(uuid) to authenticated;
grant execute on function public.betalink_group_daily_check_in(uuid,text) to authenticated;
grant execute on function public.betalink_group_remove_member(uuid,uuid) to authenticated;
grant execute on function public.betalink_tester_reliability_for_campaign(uuid,uuid) to authenticated;
