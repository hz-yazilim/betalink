-- All Betalink tester groups are public. The owner account can create groups without
-- tester eligibility or BP charges; regular users retain the 2-test and 30 BP rules.
create or replace function private.betalink_create_group(p_name text,p_description text default '',p_is_private boolean default false)
returns uuid language plpgsql security definer set search_path=public,auth,pg_temp as $$
declare uid uuid:=auth.uid(); wallet public.betalink_wallets%rowtype; new_id uuid; owner_admin boolean:=false;
begin
 if uid is null then raise exception 'Oturum gerekli.'; end if;
 owner_admin := lower(coalesce(auth.jwt()->>'email',''))='hz.yazilimm@gmail.com'
   and coalesce(auth.jwt()->>'email_confirmed_at','')<>'';
 if not owner_admin then
   if not exists(select 1 from public.betalink_wallets where user_id=uid and completed_support_tests>=2) then
     raise exception 'Grup oluşturmak için önce en az 2 uygulamanın testini tamamlamalısın.';
   end if;
   insert into public.betalink_wallets(user_id) values(uid) on conflict(user_id) do nothing;
   select * into wallet from public.betalink_wallets where user_id=uid for update;
   if wallet.balance<30 then raise exception 'Grup oluşturmak için 30 BetaPuan gerekli. Mevcut bakiyen: %',wallet.balance; end if;
 end if;
 if char_length(trim(p_name)) not between 2 and 80 then raise exception 'Grup adı 2-80 karakter olmalı.'; end if;
 insert into public.tester_groups(name,description,is_private,created_by)
 values(trim(p_name),coalesce(p_description,''),false,uid) returning id into new_id;
 insert into public.group_members(group_id,user_id,member_role,status) values(new_id,uid,'leader','approved')
 on conflict do nothing;
 if not owner_admin then
   update public.betalink_wallets set balance=balance-30,updated_at=now() where user_id=uid;
   insert into public.betalink_wallet_ledger(user_id,amount,entry_type,description,group_id)
   values(uid,-30,'group_create','Tester grubu oluşturma bedeli',new_id);
 end if;
 return new_id;
end; $$;
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
 if exists(select 1 from public.group_members where group_id=p_group_id and user_id=uid) then raise exception 'Bu gruba zaten katılmışsın.'; end if;
 select count(*) into current_count from public.group_members where group_id=p_group_id and member_role='member' and status in ('approved','pending');
 if current_count>=15 then raise exception 'Grup 15 tester kapasitesini doldurdu.'; end if;
 insert into public.betalink_wallets(user_id) values(uid) on conflict(user_id) do nothing;
 select * into wallet from public.betalink_wallets where user_id=uid for update;
 if wallet.balance<cost then raise exception 'Açık gruba katılmak için 5 BetaPuan gerekli. Mevcut bakiyen: %',wallet.balance; end if;
 insert into public.group_members(group_id,user_id,member_role,status) values(p_group_id,uid,'member','approved');
 update public.betalink_wallets set balance=balance-cost,updated_at=now() where user_id=uid;
 insert into public.betalink_wallet_ledger(user_id,amount,entry_type,description,group_id) values(uid,-cost,'group_join','Açık tester grubuna katılım bedeli',p_group_id);
 return 'approved';
end; $$;
revoke all on function private.betalink_create_group(text,text,boolean) from public,anon;
revoke all on function private.betalink_join_group(uuid) from public,anon;
grant execute on function private.betalink_create_group(text,text,boolean) to authenticated;
grant execute on function private.betalink_join_group(uuid) to authenticated;
update public.tester_groups set is_private=false where is_private=true;
