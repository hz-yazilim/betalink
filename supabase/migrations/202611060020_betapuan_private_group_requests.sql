create or replace function public.betalink_join_group(p_group_id uuid)
returns text language plpgsql security definer set search_path=public,pg_temp as $$
declare uid uuid:=auth.uid(); grp public.tester_groups%rowtype; wallet public.betalink_wallets%rowtype; cost integer:=5;
begin
 if uid is null then raise exception 'Oturum gerekli.'; end if;
 if (select count(*) from public.betalink_wallets where user_id=uid and completed_support_tests>=2)=0 then
   raise exception 'Gruplara katılmak için önce en az 2 uygulamanın testini tamamlamalısın.';
 end if;
 select * into grp from public.tester_groups where id=p_group_id;
 if not found then raise exception 'Grup bulunamadı.'; end if;
 if grp.created_by=uid then raise exception 'Kendi grubuna zaten sahipsin.'; end if;
 if exists(select 1 from public.group_members where group_id=p_group_id and user_id=uid) then
   raise exception 'Bu gruba zaten katılmışsın veya isteğin bekliyor.';
 end if;
 if grp.is_private then
   insert into public.group_members(group_id,user_id,member_role,status) values(p_group_id,uid,'member','pending');
   return 'pending';
 end if;
 insert into public.betalink_wallets(user_id) values(uid) on conflict(user_id) do nothing;
 select * into wallet from public.betalink_wallets where user_id=uid for update;
 if wallet.balance<cost then raise exception 'Açık gruba katılmak için 5 BetaPuan gerekli. Mevcut bakiyen: %',wallet.balance; end if;
 insert into public.group_members(group_id,user_id,member_role,status) values(p_group_id,uid,'member','approved');
 update public.betalink_wallets set balance=balance-cost,updated_at=now() where user_id=uid;
 insert into public.betalink_wallet_ledger(user_id,amount,entry_type,description,group_id) values(uid,-cost,'group_join','Açık tester grubuna katılım bedeli',p_group_id);
 return 'approved';
end; $$;
revoke all on function public.betalink_join_group(uuid) from public,anon;
grant execute on function public.betalink_join_group(uuid) to authenticated;