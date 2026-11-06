create or replace function public.guard_betalink_group_capacity()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare current_count integer;
begin
 if new.status='approved' and new.member_role='member' and (tg_op='INSERT' or old.status is distinct from 'approved') then
  perform 1 from public.tester_groups where id=new.group_id for update;
  select count(*) into current_count from public.group_members
   where group_id=new.group_id and status='approved' and member_role='member'
    and (tg_op='INSERT' or id<>new.id);
  if current_count>=15 then raise exception 'Bu grup 15 tester kapasitesini doldurdu.'; end if;
 end if;
 return new;
end; $$;

create or replace function public.refresh_betalink_group_clock()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare gid uuid; tester_count integer; current_status text;
begin
 gid:=case when tg_op='DELETE' then old.group_id else new.group_id end;
 perform 1 from public.tester_groups where id=gid for update;
 select count(*) into tester_count from public.group_members
  where group_id=gid and status='approved' and member_role='member';
 select tracking_status into current_status from public.tester_groups where id=gid;
 if current_status='completed' then return coalesce(new,old); end if;
 if tester_count>=15 and current_status in ('waiting','paused') then
  update public.tester_groups set tracking_status='running',started_at=now(),completed_at=null where id=gid;
  insert into public.notifications(recipient_id,notification_type,title,body)
   select user_id,'group_test_started','Grup testi başladı','Grubunuz 15 onaylı testere ulaştı. 14 günlük test süreci başladı.'
   from public.group_members where group_id=gid and status='approved';
 elsif tester_count<15 and current_status='running' then
  update public.tester_groups set tracking_status='paused',started_at=null where id=gid;
  insert into public.notifications(recipient_id,notification_type,title,body)
   select user_id,'group_test_paused','Grup testi duraklatıldı','Onaylı tester sayısı 15 kişinin altına düştü. 15 tester yeniden tamamlandığında 14 günlük süre baştan başlayacak.'
   from public.group_members where group_id=gid and status='approved';
 end if;
 return coalesce(new,old);
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
 if exists(select 1 from public.group_members where group_id=p_group_id and user_id=uid) then
  raise exception 'Bu gruba zaten katılmışsın veya isteğin bekliyor.';
 end if;
 select count(*) into current_count from public.group_members
  where group_id=p_group_id and member_role='member' and status in ('approved','pending');
 if current_count>=15 then raise exception 'Grup 15 tester kapasitesini doldurdu.'; end if;
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
 select count(*) into n from public.group_members where group_id=p_group_id and status='approved' and member_role='member';
 if g.tracking_status='running' and g.started_at <= now()-make_interval(days=>g.required_days) then
  update public.tester_groups set tracking_status='completed',completed_at=now() where id=p_group_id returning * into g;
  insert into public.betalink_wallets(user_id)
   select user_id from public.group_members where group_id=p_group_id and status='approved' and member_role='member'
   on conflict(user_id) do nothing;
  with rewarded as (
   insert into public.betalink_wallet_ledger(user_id,amount,entry_type,description,group_id)
    select user_id,25,'group_test_reward','15 testerlik grup testini 14 gün tamamladı',p_group_id
    from public.group_members where group_id=p_group_id and status='approved' and member_role='member'
   on conflict(user_id,group_id,entry_type) where entry_type='group_test_reward' do nothing
   returning user_id
  )
  update public.betalink_wallets w set balance=w.balance+25,lifetime_earned=w.lifetime_earned+25,
   completed_support_tests=w.completed_support_tests+1,updated_at=now()
   from rewarded r where w.user_id=r.user_id;
  insert into public.notifications(recipient_id,notification_type,title,body)
   select user_id,'group_test_completed','Grup testi tamamlandı','Grubunuzun 14 günlük test süreci tamamlandı. 25 BetaPuan hesabına eklendi.'
   from public.group_members where group_id=p_group_id and status='approved';
 end if;
 select jsonb_build_object(
  'id',g.id,'name',g.name,'tracking_status',g.tracking_status,'approved_members',n,
  'required_members',g.required_members,'required_days',g.required_days,'started_at',g.started_at,
  'completed_at',g.completed_at,
  'my_email_shared',coalesce((select share_test_email from public.group_members where group_id=p_group_id and user_id=uid),false),
  'my_play_test_email',coalesce((select play_test_email from public.group_members where group_id=p_group_id and user_id=uid),''),
  'elapsed_days',case when g.started_at is null then 0 else least(g.required_days,greatest(0,floor(extract(epoch from (coalesce(g.completed_at,now())-g.started_at))/86400)::integer)) end,
  'test_emails',case when g.tracking_status in ('running','completed') then
   coalesce((select jsonb_agg(jsonb_build_object('display_name',p.display_name,'email',gm.play_test_email) order by gm.joined_at)
    from public.group_members gm left join public.profiles p on p.user_id=gm.user_id
    where gm.group_id=p_group_id and gm.status='approved' and gm.member_role='member'
     and gm.share_test_email=true and gm.play_test_email is not null),'[]'::jsonb)
   else '[]'::jsonb end
 ) into result;
 return result;
end; $$;