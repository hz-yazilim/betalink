create or replace function public.betalink_admin_adjust_bp(
  p_user_ids uuid[], p_amount integer, p_reason text, p_actor uuid
) returns integer language plpgsql security definer set search_path = ''
as $fn$
declare v_count integer;
begin
  if coalesce(pg_catalog.current_setting('request.jwt.claim.role', true), '') <> 'service_role'
     and session_user not in ('postgres','supabase_admin') then
    raise exception 'Yönetici işlemi için sistem yetkisi gerekli.';
  end if;
  if p_actor is null or p_amount is null or p_amount = 0 or pg_catalog.abs(p_amount) > 100000
     or p_reason is null or pg_catalog.char_length(pg_catalog.btrim(p_reason)) not between 3 and 240
     or p_user_ids is null or pg_catalog.cardinality(p_user_ids) not between 1 and 500 then
    raise exception 'Puan düzenleme verileri geçersiz.';
  end if;
  if (select pg_catalog.count(distinct x) from pg_catalog.unnest(p_user_ids) x) <> pg_catalog.cardinality(p_user_ids) then
    raise exception 'Kullanıcı listesinde tekrar var.';
  end if;
  if (select pg_catalog.count(*) from public.profiles p where p.user_id = any(p_user_ids)) <> pg_catalog.cardinality(p_user_ids) then
    raise exception 'Bir veya daha fazla kullanıcı bulunamadı.';
  end if;
  insert into public.betalink_wallets(user_id)
  select x from pg_catalog.unnest(p_user_ids) x on conflict(user_id) do nothing;
  perform 1 from public.betalink_wallets w where w.user_id = any(p_user_ids) order by w.user_id for update;
  if exists(select 1 from public.betalink_wallets w where w.user_id = any(p_user_ids) and w.balance + p_amount < 0) then
    raise exception 'İşlem bir veya daha fazla bakiyeyi sıfırın altına düşürür.';
  end if;
  update public.betalink_wallets w
  set balance = w.balance + p_amount,
      lifetime_earned = w.lifetime_earned + pg_catalog.greatest(0,p_amount),
      updated_at = pg_catalog.now()
  where w.user_id = any(p_user_ids);
  insert into public.betalink_wallet_ledger(user_id,amount,entry_type,description)
  select x,p_amount,'admin_adjustment',pg_catalog.btrim(p_reason) from pg_catalog.unnest(p_user_ids) x;
  insert into public.admin_audit_logs(actor_id,action,target_type,target_id,metadata)
  values(p_actor,'adjust_bp','users',null,pg_catalog.jsonb_build_object(
    'user_count',pg_catalog.cardinality(p_user_ids),'amount',p_amount,'reason',pg_catalog.btrim(p_reason)));
  get diagnostics v_count = row_count;
  return pg_catalog.cardinality(p_user_ids);
end;
$fn$;

create or replace function public.betalink_admin_adjust_reputation(
  p_user_ids uuid[], p_amount integer, p_reason text, p_actor uuid
) returns integer language plpgsql security definer set search_path = ''
as $fn$
declare v_user uuid; v_balance bigint;
begin
  if coalesce(pg_catalog.current_setting('request.jwt.claim.role', true), '') <> 'service_role'
     and session_user not in ('postgres','supabase_admin') then
    raise exception 'Yönetici işlemi için sistem yetkisi gerekli.';
  end if;
  if p_actor is null or p_amount is null or p_amount = 0 or pg_catalog.abs(p_amount) > 100000
     or p_reason is null or pg_catalog.char_length(pg_catalog.btrim(p_reason)) not between 3 and 240
     or p_user_ids is null or pg_catalog.cardinality(p_user_ids) not between 1 and 500 then
    raise exception 'İtibar düzenleme verileri geçersiz.';
  end if;
  if (select pg_catalog.count(distinct x) from pg_catalog.unnest(p_user_ids) x) <> pg_catalog.cardinality(p_user_ids) then
    raise exception 'Kullanıcı listesinde tekrar var.';
  end if;
  if (select pg_catalog.count(*) from public.profiles p where p.user_id = any(p_user_ids)) <> pg_catalog.cardinality(p_user_ids) then
    raise exception 'Bir veya daha fazla kullanıcı bulunamadı.';
  end if;
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_user::text, 7))
  from pg_catalog.unnest(p_user_ids) as u(v_user) order by v_user;
  foreach v_user in array p_user_ids loop
    select coalesce(pg_catalog.sum(re.points),0) into v_balance
    from public.reputation_events re where re.user_id = v_user;
    if v_balance + p_amount < 0 then
      raise exception 'İşlem bir veya daha fazla itibar puanını sıfırın altına düşürür.';
    end if;
  end loop;
  insert into public.reputation_events(user_id,event_type,points,reason)
  select x,'admin_adjustment',p_amount,pg_catalog.btrim(p_reason) from pg_catalog.unnest(p_user_ids) x;
  insert into public.admin_audit_logs(actor_id,action,target_type,target_id,metadata)
  values(p_actor,'adjust_reputation','users',null,pg_catalog.jsonb_build_object(
    'user_count',pg_catalog.cardinality(p_user_ids),'amount',p_amount,'reason',pg_catalog.btrim(p_reason)));
  return pg_catalog.cardinality(p_user_ids);
end;
$fn$;

revoke all on function public.betalink_admin_adjust_bp(uuid[],integer,text,uuid) from public,anon,authenticated;
revoke all on function public.betalink_admin_adjust_reputation(uuid[],integer,text,uuid) from public,anon,authenticated;
grant execute on function public.betalink_admin_adjust_bp(uuid[],integer,text,uuid) to service_role;
grant execute on function public.betalink_admin_adjust_reputation(uuid[],integer,text,uuid) to service_role;
