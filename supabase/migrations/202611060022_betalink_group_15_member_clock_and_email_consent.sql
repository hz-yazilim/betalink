-- Betalink groups: 15 approved members start one shared, continuous 14-day test window.
alter table public.tester_groups
  add column if not exists required_members integer not null default 15 check (required_members = 15),
  add column if not exists required_days integer not null default 14 check (required_days = 14),
  add column if not exists tracking_status text not null default 'waiting'
    check (tracking_status in ('waiting','running','paused','completed')),
  add column if not exists started_at timestamptz,
  add column if not exists completed_at timestamptz;

alter table public.group_members
  add column if not exists share_test_email boolean not null default false;

create index if not exists group_members_group_status_idx on public.group_members(group_id,status);

create or replace function public.guard_betalink_group_capacity()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare current_count integer;
begin
  if new.status='approved' and (tg_op='INSERT' or old.status is distinct from 'approved') then
    perform 1 from public.tester_groups where id=new.group_id for update;
    select count(*) into current_count from public.group_members
      where group_id=new.group_id and status='approved'
        and (tg_op='INSERT' or id<>new.id);
    if current_count>=15 then raise exception 'Bu grup 15 kişilik kapasitesini doldurdu.'; end if;
  end if;
  return new;
end; $$;

create or replace function public.refresh_betalink_group_clock()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare gid uuid; approved_count integer; current_status text;
begin
  gid:=case when tg_op='DELETE' then old.group_id else new.group_id end;
  perform 1 from public.tester_groups where id=gid for update;
  select count(*) into approved_count from public.group_members where group_id=gid and status='approved';
  select tracking_status into current_status from public.tester_groups where id=gid;
  if current_status='completed' then return coalesce(new,old); end if;
  if approved_count>=15 and current_status in ('waiting','paused') then
    update public.tester_groups set tracking_status='running',started_at=now(),completed_at=null where id=gid;
    insert into public.notifications(recipient_id,notification_type,title,body)
      select user_id,'group_test_started','Grup testi başladı','Grubunuz 15 onaylı üyeye ulaştı. 14 günlük test süreci başladı.'
      from public.group_members where group_id=gid and status='approved';
  elsif approved_count<15 and current_status='running' then
    update public.tester_groups set tracking_status='paused',started_at=null where id=gid;
    insert into public.notifications(recipient_id,notification_type,title,body)
      select user_id,'group_test_paused','Grup testi duraklatıldı','Onaylı üye sayısı 15 kişinin altına düştü. 15 kişi yeniden tamamlandığında 14 günlük süre baştan başlayacak.'
      from public.group_members where group_id=gid and status='approved';
  end if;
  return coalesce(new,old);
end; $$;

revoke all on function public.guard_betalink_group_capacity() from public,anon,authenticated;
revoke all on function public.refresh_betalink_group_clock() from public,anon,authenticated;
drop trigger if exists betalink_group_capacity_guard on public.group_members;
create trigger betalink_group_capacity_guard before insert or update of status on public.group_members
for each row execute function public.guard_betalink_group_capacity();
drop trigger if exists betalink_group_clock_refresh on public.group_members;
create trigger betalink_group_clock_refresh after insert or update of status or delete on public.group_members
for each row execute function public.refresh_betalink_group_clock();

create or replace function private.betalink_set_group_email_consent(p_group_id uuid,p_consent boolean)
returns void language plpgsql security definer set search_path=public,auth,pg_temp as $$
begin
  if auth.uid() is null then raise exception 'Oturum gerekli.'; end if;
  update public.group_members set share_test_email=p_consent
   where group_id=p_group_id and user_id=auth.uid() and status='approved';
  if not found then raise exception 'Bu grupta onaylı üyeliğin bulunmuyor.'; end if;
end; $$;

create or replace function private.betalink_group_snapshot(p_group_id uuid)
returns jsonb language plpgsql security definer set search_path=public,auth,pg_temp as $$
declare uid uuid:=auth.uid(); g public.tester_groups%rowtype; n integer; result jsonb;
begin
  if uid is null then raise exception 'Oturum gerekli.'; end if;
  select * into g from public.tester_groups where id=p_group_id;
  if not found then raise exception 'Grup bulunamadı.'; end if;
  if not exists(select 1 from public.group_members where group_id=p_group_id and user_id=uid and status='approved') then
    raise exception 'Grup üyesi erişimi gerekli.';
  end if;
  select count(*) into n from public.group_members where group_id=p_group_id and status='approved';
  if g.tracking_status='running' and g.started_at <= now()-make_interval(days=>g.required_days) then
    update public.tester_groups set tracking_status='completed',completed_at=now()
      where id=p_group_id returning * into g;
    insert into public.notifications(recipient_id,notification_type,title,body)
      select user_id,'group_test_completed','Grup testi tamamlandı','Grubunuzun 14 günlük test süreci tamamlandı.'
      from public.group_members where group_id=p_group_id and status='approved';
  end if;
  select jsonb_build_object(
    'id',g.id,'name',g.name,'tracking_status',g.tracking_status,'approved_members',n,
    'required_members',g.required_members,'required_days',g.required_days,
    'started_at',g.started_at,'completed_at',g.completed_at,
    'elapsed_days',case when g.started_at is null then 0 else least(g.required_days,greatest(0,floor(extract(epoch from (coalesce(g.completed_at,now())-g.started_at))/86400)::integer)) end,
    'test_emails',case when g.tracking_status in ('running','completed') then
      coalesce((select jsonb_agg(jsonb_build_object('display_name',p.display_name,'email',u.email) order by gm.joined_at)
       from public.group_members gm join auth.users u on u.id=gm.user_id
       left join public.profiles p on p.user_id=gm.user_id
       where gm.group_id=p_group_id and gm.status='approved' and gm.share_test_email=true and u.email is not null),'[]'::jsonb)
      else '[]'::jsonb end
  ) into result;
  return result;
end; $$;

grant execute on function private.betalink_set_group_email_consent(uuid,boolean) to authenticated;
grant execute on function private.betalink_group_snapshot(uuid) to authenticated;
create or replace function public.betalink_set_group_email_consent(p_group_id uuid,p_consent boolean)
returns void language sql security invoker set search_path=public,private,pg_temp as $$
 select private.betalink_set_group_email_consent(p_group_id,p_consent);
$$;
create or replace function public.betalink_group_snapshot(p_group_id uuid)
returns jsonb language sql security invoker set search_path=public,private,pg_temp as $$
 select private.betalink_group_snapshot(p_group_id);
$$;
revoke all on function public.betalink_set_group_email_consent(uuid,boolean) from public,anon;
revoke all on function public.betalink_group_snapshot(uuid) from public,anon;
grant execute on function public.betalink_set_group_email_consent(uuid,boolean) to authenticated;
grant execute on function public.betalink_group_snapshot(uuid) to authenticated;

create or replace function private.betalink_join_group(p_group_id uuid)
returns text language plpgsql security definer set search_path=public,pg_temp as $$
declare uid uuid:=auth.uid(); grp public.tester_groups%rowtype; wallet public.betalink_wallets%rowtype; current_count integer; cost integer:=5;
begin
 if uid is null then raise exception 'Oturum gerekli.'; end if;
 if not exists(select 1 from public.betalink_wallets where user_id=uid and completed_support_tests>=2) then
   raise exception 'Gruplara katılmak için önce en az 2 uygulamanın testini tamamlamalısın.';
 end if;
 select * into grp from public.tester_groups where id=p_group_id for update;
 if not found then raise exception 'Grup bulunamadı.'; end if;
 if grp.created_by=uid then raise exception 'Kendi grubuna zaten sahipsin.'; end if;
 if exists(select 1 from public.group_members where group_id=p_group_id and user_id=uid) then
   raise exception 'Bu gruba zaten katılmışsın veya isteğin bekliyor.';
 end if;
 select count(*) into current_count from public.group_members where group_id=p_group_id and status in ('approved','pending');
 if current_count>=15 then raise exception 'Grup 15 kişilik kapasitesini doldurdu.'; end if;
 if grp.is_private then
   insert into public.group_members(group_id,user_id,member_role,status) values(p_group_id,uid,'member','pending');
   return 'pending';
 end if;
 insert into public.betalink_wallets(user_id) values(uid) on conflict(user_id) do nothing;
 select * into wallet from public.betalink_wallets where user_id=uid for update;
 if wallet.balance<cost then raise exception 'Açık gruba katılmak için 5 BetaPuan gerekli. Mevcut bakiyen: %',wallet.balance; end if;
 insert into public.group_members(group_id,user_id,member_role,status) values(p_group_id,uid,'member','approved');
 update public.betalink_wallets set balance=balance-cost,updated_at=now() where user_id=uid;
 insert into public.betalink_wallet_ledger(user_id,amount,entry_type,description,group_id)
 values(uid,-cost,'group_join','Açık tester grubuna katılım bedeli',p_group_id);
 return 'approved';
end; $$;
grant execute on function private.betalink_join_group(uuid) to authenticated;
create or replace function public.betalink_join_group(p_group_id uuid)
returns text language sql security invoker set search_path=public,private,pg_temp as $$
 select private.betalink_join_group(p_group_id);
$$;
revoke all on function public.betalink_join_group(uuid) from public,anon;
grant execute on function public.betalink_join_group(uuid) to authenticated;
