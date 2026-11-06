drop policy if exists "task_completions_update_campaign_owner" on public.task_completions;
drop policy if exists "task_completions_resubmit_self" on public.task_completions;

create policy "task_completions_update_owner_or_resubmit"
on public.task_completions for update to authenticated
using (
  (tester_id = (select auth.uid()) and status = 'needs_changes')
  or exists (
    select 1 from public.campaign_tasks t
    join public.campaigns c on c.id = t.campaign_id
    where t.id = task_id and c.developer_id = (select auth.uid())
  )
)
with check (
  (tester_id = (select auth.uid()) and status = 'submitted')
  or exists (
    select 1 from public.campaign_tasks t
    join public.campaigns c on c.id = t.campaign_id
    where t.id = task_id and c.developer_id = (select auth.uid())
  )
);
