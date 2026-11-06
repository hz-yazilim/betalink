-- Permit a tester to resubmit only a task that the campaign owner sent back.
create or replace function public.guard_betalink_task_resubmission()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
  if auth.uid() is not null and auth.uid() = old.tester_id then
    if old.status <> 'needs_changes'
       or new.status <> 'submitted'
       or new.tester_id <> old.tester_id
       or new.task_id <> old.task_id then
      raise exception 'Testers may only resubmit their own task after changes were requested'
        using errcode = '42501';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists guard_betalink_task_resubmission on public.task_completions;
create trigger guard_betalink_task_resubmission
before update on public.task_completions
for each row execute function public.guard_betalink_task_resubmission();

drop policy if exists "task_completions_resubmit_self" on public.task_completions;
create policy "task_completions_resubmit_self"
on public.task_completions for update to authenticated
using (tester_id = (select auth.uid()) and status = 'needs_changes')
with check (tester_id = (select auth.uid()) and status = 'submitted');
