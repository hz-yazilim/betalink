-- Keep auth.uid() out of per-row RLS evaluation plans.
drop policy if exists bug_reports_insert_as_campaign_tester on public.bug_reports;
create policy bug_reports_insert_as_campaign_tester
on public.bug_reports for insert to authenticated
with check (
  reporter_id = (select auth.uid())
  and exists (
    select 1 from public.campaign_members m
    where m.campaign_id = bug_reports.campaign_id
      and m.tester_id = (select auth.uid())
      and m.status in ('accepted','opted_in','active','completed')
  )
);

drop policy if exists bug_reports_read_reporter_or_campaign_owner on public.bug_reports;
create policy bug_reports_read_reporter_or_campaign_owner
on public.bug_reports for select to authenticated
using (
  reporter_id = (select auth.uid())
  or exists (
    select 1 from public.campaigns c
    where c.id = bug_reports.campaign_id and c.developer_id = (select auth.uid())
  )
);
