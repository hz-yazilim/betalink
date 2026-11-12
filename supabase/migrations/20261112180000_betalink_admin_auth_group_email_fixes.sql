-- Betalink production fixes: reliable admin service RPC, verified-email profiles, and consented account Gmail visibility.
-- Admin authorization is enforced by the owner-verified Edge Function plus EXECUTE privilege:
-- only service_role may invoke these SECURITY DEFINER functions.
create or replace function public.betalink_admin_adjust_bp(
  p_user_ids uuid[], p_amount integer, p_reason text, p_actor uuid
) returns integer language plpgsql security definer set search_path = ''
as $fn$
begin
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
      lifetime_earned = w.lifetime_earned + greatest(0,p_amount),
      updated_at = pg_catalog.now()
  where w.user_id = any(p_user_ids);
  insert into public.betalink_wallet_ledger(user_id,amount,entry_type,description)
  select x,p_amount,'admin_adjustment',pg_catalog.btrim(p_reason) from pg_catalog.unnest(p_user_ids) x;
  insert into public.admin_audit_logs(actor_id,action,target_type,target_id,metadata)
  values(p_actor,'adjust_bp','users',null,pg_catalog.jsonb_build_object(
    'user_count',pg_catalog.cardinality(p_user_ids),'amount',p_amount,'reason',pg_catalog.btrim(p_reason)));
  return pg_catalog.cardinality(p_user_ids);
end;
$fn$;

create or replace function public.betalink_admin_adjust_reputation(
  p_user_ids uuid[], p_amount integer, p_reason text, p_actor uuid
) returns integer language plpgsql security definer set search_path = ''
as $fn$
declare v_user uuid; v_balance bigint;
begin
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

-- Unverified sign-ups remain in Auth only; they do not get a public profile or appear in the app.
create or replace function public.handle_new_betalink_user()
returns trigger language plpgsql security definer set search_path = public, pg_temp
as $$
begin
  if new.email_confirmed_at is not null then
    insert into public.profiles(user_id,display_name,country_code)
    values(new.id,coalesce(nullif(new.raw_user_meta_data ->> 'display_name',''),split_part(new.email,'@',1)),'TR')
    on conflict(user_id) do nothing;
  end if;
  return new;
end;
$$;
revoke all on function public.handle_new_betalink_user() from public,anon,authenticated;
drop trigger if exists on_betalink_auth_user_created on auth.users;
drop trigger if exists on_betalink_auth_user_verified on auth.users;
create trigger on_betalink_auth_user_created after insert on auth.users
for each row execute function public.handle_new_betalink_user();
create trigger on_betalink_auth_user_verified after update of email_confirmed_at on auth.users
for each row when (new.email_confirmed_at is not null)
execute function public.handle_new_betalink_user();

-- Separate account Gmail from a manually supplied Play-test address; sharing is opt-in.
alter table public.group_members
  add column if not exists share_account_email boolean not null default false;

create or replace function private.betalink_group_set_account_email_consent(p_group_id uuid,p_share boolean)
returns boolean language plpgsql security definer set search_path = public,auth,private,pg_temp
as $$
declare uid uuid:=auth.uid(); affected integer;
begin
  if uid is null then raise exception 'Oturum gerekli.'; end if;
  if p_share is null then raise exception 'Paylaşım tercihi gerekli.'; end if;
  update public.group_members
    set share_account_email=p_share
    where group_id=p_group_id and user_id=uid and status='approved';
  get diagnostics affected=row_count;
  if affected=0 then raise exception 'Bu grubun onaylı üyesi değilsin.'; end if;
  return true;
end;
$$;

create or replace function public.betalink_group_set_account_email_consent(p_group_id uuid,p_share boolean)
returns boolean language sql security invoker set search_path=public,private,pg_temp
as $$ select private.betalink_group_set_account_email_consent(p_group_id,p_share); $$;

create or replace function private.betalink_group_account_email_consent(p_group_id uuid)
returns boolean language sql security definer set search_path=public,auth,private,pg_temp
as $$
  select coalesce((
    select gm.share_account_email from public.group_members gm
    where gm.group_id=p_group_id and gm.user_id=auth.uid() and gm.status='approved'
  ),false);
$$;

create or replace function public.betalink_group_account_email_consent(p_group_id uuid)
returns boolean language sql security invoker set search_path=public,private,pg_temp
as $$ select private.betalink_group_account_email_consent(p_group_id); $$;

create or replace function private.betalink_group_member_account_emails(p_group_id uuid)
returns table(user_id uuid,display_name text,account_email text)
language plpgsql security definer set search_path=public,auth,private,pg_temp
as $$
declare uid uuid:=auth.uid(); owner_id uuid;
begin
  if uid is null then raise exception 'Oturum gerekli.'; end if;
  select tg.created_by into owner_id from public.tester_groups tg where tg.id=p_group_id;
  if owner_id is distinct from uid then raise exception 'Yalnızca grup kurucusu üye hesap e-postalarını görebilir.'; end if;
  return query
    select gm.user_id,coalesce(p.display_name,'Betalink kullanıcısı'),coalesce(u.email,'')
    from public.group_members gm
    join auth.users u on u.id=gm.user_id
    left join public.profiles p on p.user_id=gm.user_id
    where gm.group_id=p_group_id and gm.status in ('approved','pending')
      and gm.share_account_email=true
    order by gm.joined_at asc;
end;
$$;

create or replace function public.betalink_group_member_account_emails(p_group_id uuid)
returns table(user_id uuid,display_name text,account_email text)
language sql security invoker set search_path=public,private,pg_temp
as $$ select * from private.betalink_group_member_account_emails(p_group_id); $$;

revoke all on function private.betalink_group_set_account_email_consent(uuid,boolean) from public,anon;
revoke all on function private.betalink_group_account_email_consent(uuid) from public,anon;
revoke all on function private.betalink_group_member_account_emails(uuid) from public,anon;
grant execute on function private.betalink_group_set_account_email_consent(uuid,boolean) to authenticated;
grant execute on function private.betalink_group_account_email_consent(uuid) to authenticated;
grant execute on function private.betalink_group_member_account_emails(uuid) to authenticated;
revoke all on function public.betalink_group_set_account_email_consent(uuid,boolean) from public,anon;
revoke all on function public.betalink_group_account_email_consent(uuid) from public,anon;
revoke all on function public.betalink_group_member_account_emails(uuid) from public,anon;
grant execute on function public.betalink_group_set_account_email_consent(uuid,boolean) to authenticated;
grant execute on function public.betalink_group_account_email_consent(uuid) to authenticated;
grant execute on function public.betalink_group_member_account_emails(uuid) to authenticated;
