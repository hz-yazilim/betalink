-- PostgreSQL COALESCE/GREATEST are special expressions, not schema-qualified functions.
create or replace function public.betalink_campaign_feedback_summary(p_campaign_id uuid)
returns jsonb language plpgsql security definer set search_path = ''
as $fn$
declare actor uuid:=auth.uid(); owner_id uuid; result jsonb;
begin
  if actor is null then raise exception 'Oturum gerekli.'; end if;
  select c.developer_id into owner_id from public.campaigns c where c.id=p_campaign_id;
  if owner_id is distinct from actor then raise exception 'Bu raporu görüntüleme yetkin yok.'; end if;
  select pg_catalog.jsonb_build_object(
    'feedback_count',pg_catalog.count(*),
    'average_rating',pg_catalog.round(pg_catalog.avg(f.rating)::numeric,2),
    'bugs',pg_catalog.count(*) filter(where f.feedback_type='bug'),
    'experiences',pg_catalog.count(*) filter(where f.feedback_type='experience'),
    'suggestions',pg_catalog.count(*) filter(where f.feedback_type='suggestion'),
    'resolved',pg_catalog.count(*) filter(where f.status='resolved'),
    'highlights',coalesce(pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object(
      'type',f.feedback_type,'rating',f.rating,'body',pg_catalog.left(f.body,500),'created_at',f.created_at
    ) order by f.created_at desc),'[]'::jsonb)
  ) into result from public.feedback f where f.campaign_id=p_campaign_id;
  return result;
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
    'reputation_points',(select coalesce(pg_catalog.sum(re.points),0) from public.reputation_events re where re.user_id=p_tester_id)
  ) into result from public.campaign_members cm where cm.campaign_id=p_campaign_id and cm.tester_id=p_tester_id;
  return result;
end;
$fn$;

create or replace function public.betalink_admin_adjust_bp(p_user_ids uuid[],p_amount integer,p_reason text,p_actor uuid)
returns integer language plpgsql security definer set search_path = ''
as $fn$
begin
  if coalesce(pg_catalog.current_setting('request.jwt.claim.role',true),'') <> 'service_role'
     and session_user not in ('postgres','supabase_admin') then raise exception 'Sistem yetkisi gerekli.'; end if;
  if p_actor is null or p_amount is null or p_amount=0 or pg_catalog.abs(p_amount)>100000
     or p_reason is null or pg_catalog.char_length(pg_catalog.btrim(p_reason)) not between 3 and 240
     or p_user_ids is null or pg_catalog.cardinality(p_user_ids) not between 1 and 500 then raise exception 'Geçersiz puan düzenleme.'; end if;
  if (select pg_catalog.count(distinct x) from pg_catalog.unnest(p_user_ids) x) <> pg_catalog.cardinality(p_user_ids) then raise exception 'Tekrarlanan kullanıcı.'; end if;
  if (select pg_catalog.count(*) from public.profiles p where p.user_id=any(p_user_ids)) <> pg_catalog.cardinality(p_user_ids) then raise exception 'Kullanıcı bulunamadı.'; end if;
  insert into public.betalink_wallets(user_id) select x from pg_catalog.unnest(p_user_ids) x on conflict(user_id) do nothing;
  perform 1 from public.betalink_wallets w where w.user_id=any(p_user_ids) order by w.user_id for update;
  if exists(select 1 from public.betalink_wallets w where w.user_id=any(p_user_ids) and w.balance+p_amount<0) then raise exception 'Bakiye sıfırın altına inemez.'; end if;
  update public.betalink_wallets w set balance=w.balance+p_amount,lifetime_earned=w.lifetime_earned+greatest(0,p_amount),updated_at=pg_catalog.now() where w.user_id=any(p_user_ids);
  insert into public.betalink_wallet_ledger(user_id,amount,entry_type,description) select x,p_amount,'admin_adjustment',pg_catalog.btrim(p_reason) from pg_catalog.unnest(p_user_ids) x;
  insert into public.admin_audit_logs(actor_id,action,target_type,target_id,metadata) values(p_actor,'adjust_bp','users',null,pg_catalog.jsonb_build_object('user_count',pg_catalog.cardinality(p_user_ids),'amount',p_amount,'reason',pg_catalog.btrim(p_reason)));
  return pg_catalog.cardinality(p_user_ids);
end;
$fn$;
