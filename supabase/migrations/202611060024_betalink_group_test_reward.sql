create unique index if not exists betalink_group_reward_once
 on public.betalink_wallet_ledger(user_id,group_id,entry_type)
 where entry_type='group_test_reward';

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
  update public.tester_groups set tracking_status='completed',completed_at=now() where id=p_group_id returning * into g;

  insert into public.betalink_wallets(user_id)
   select user_id from public.group_members where group_id=p_group_id and status='approved'
   on conflict(user_id) do nothing;

  with rewarded as (
   insert into public.betalink_wallet_ledger(user_id,amount,entry_type,description,group_id)
    select user_id,25,'group_test_reward','15 kişilik grup testini 14 gün tamamladı',p_group_id
    from public.group_members where group_id=p_group_id and status='approved'
   on conflict(user_id,group_id,entry_type) where entry_type='group_test_reward' do nothing
   returning user_id
  )
  update public.betalink_wallets w
   set balance=w.balance+25,lifetime_earned=w.lifetime_earned+25,
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