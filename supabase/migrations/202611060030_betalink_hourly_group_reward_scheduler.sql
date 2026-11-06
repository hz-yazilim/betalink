create extension if not exists pg_cron with schema pg_catalog;

create or replace function private.betalink_process_due_group_rewards()
returns jsonb
language plpgsql
security definer
set search_path = public, private, pg_temp
as $$
declare
  g public.tester_groups%rowtype;
  approved_count integer;
  completed_count integer := 0;
  rewarded_count integer := 0;
begin
  for g in
    select *
    from public.tester_groups
    where tracking_status = 'running'
      and started_at is not null
      and started_at <= now() - make_interval(days => required_days)
    order by started_at
    for update skip locked
  loop
    select count(*) into approved_count
    from public.group_members
    where group_id = g.id
      and status = 'approved'
      and member_role = 'member';

    -- Do not complete or reward if the required tester count is no longer present.
    if approved_count < g.required_members then
      continue;
    end if;

    update public.tester_groups
    set tracking_status = 'completed', completed_at = now()
    where id = g.id and tracking_status = 'running';

    if not found then
      continue;
    end if;

    insert into public.betalink_wallets(user_id)
    select user_id
    from public.group_members
    where group_id = g.id and status = 'approved' and member_role = 'member'
    on conflict (user_id) do nothing;

    with rewarded as (
      insert into public.betalink_wallet_ledger(user_id, amount, entry_type, description, group_id)
      select user_id, 25, 'group_test_reward',
             '15 testerlik grup testi 14 günlük süreci tamamladı', g.id
      from public.group_members
      where group_id = g.id and status = 'approved' and member_role = 'member'
      on conflict (user_id, group_id, entry_type)
        where entry_type = 'group_test_reward' do nothing
      returning user_id
    )
    update public.betalink_wallets w
    set balance = w.balance + 25,
        lifetime_earned = w.lifetime_earned + 25,
        completed_support_tests = w.completed_support_tests + 1,
        updated_at = now()
    from rewarded r
    where w.user_id = r.user_id;

    get diagnostics rewarded_count = row_count;

    insert into public.notifications(recipient_id, notification_type, title, body)
    select user_id, 'group_test_completed', 'Grup testi tamamlandı',
           '14 günlük grup testiniz tamamlandı. 25 BetaPuan hesabınıza eklendi.'
    from public.group_members
    where group_id = g.id and status = 'approved';

    completed_count := completed_count + 1;
  end loop;

  return jsonb_build_object(
    'completed_groups', completed_count,
    'last_group_rewarded_wallets', rewarded_count,
    'checked_at', now()
  );
end;
$$;

revoke all on function private.betalink_process_due_group_rewards() from public, anon, authenticated;

do $$
begin
  if not exists (select 1 from cron.job where jobname = 'betalink-group-reward-check-hourly') then
    perform cron.schedule(
      'betalink-group-reward-check-hourly',
      '5 * * * *',
      'select private.betalink_process_due_group_rewards();'
    );
  end if;
end $$;