-- Reputation is earned from reviewed activity, not self-submitted claims.
create or replace function public.award_betalink_reputation()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if tg_table_name = 'task_completions' then
    if new.status = 'approved' and (tg_op = 'INSERT' or old.status is distinct from new.status) then
      if not exists (
        select 1 from public.reputation_events
        where user_id = new.tester_id
          and event_type = 'task_approved'
          and reason = new.id::text
      ) then
        insert into public.reputation_events(user_id, campaign_id, event_type, points, reason)
        select new.tester_id, t.campaign_id, 'task_approved', 2, new.id::text
        from public.campaign_tasks t where t.id = new.task_id;
      end if;
    end if;
  elsif tg_table_name = 'campaign_members' then
    if new.status = 'dropped' and (tg_op = 'INSERT' or old.status is distinct from new.status) then
      if not exists (
        select 1 from public.reputation_events
        where user_id = new.tester_id
          and event_type = 'campaign_dropout'
          and reason = new.id::text
      ) then
        insert into public.reputation_events(user_id, campaign_id, event_type, points, reason)
        values (new.tester_id, new.campaign_id, 'campaign_dropout', -2, new.id::text);
      end if;
    end if;
  end if;
  return new;
end;
$$;

revoke all on function public.award_betalink_reputation() from public, anon, authenticated;
drop trigger if exists on_betalink_task_reputation on public.task_completions;
create trigger on_betalink_task_reputation
after insert or update of status on public.task_completions
for each row execute function public.award_betalink_reputation();

drop trigger if exists on_betalink_dropout_reputation on public.campaign_members;
create trigger on_betalink_dropout_reputation
after insert or update of status on public.campaign_members
for each row execute function public.award_betalink_reputation();
