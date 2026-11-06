-- Index foreign keys used in ownership, moderation, and participant lookups.
create index if not exists admin_audit_logs_actor_idx on public.admin_audit_logs(actor_id);
create index if not exists blocked_users_blocked_idx on public.blocked_users(blocked_id);
create index if not exists campaign_daily_checkins_tester_idx on public.campaign_daily_checkins(tester_id);
create index if not exists feedback_tester_idx on public.feedback(tester_id);
create index if not exists group_members_user_idx on public.group_members(user_id);
create index if not exists platform_admins_granted_by_idx on public.platform_admins(granted_by);
create index if not exists reports_campaign_idx on public.reports(campaign_id);
create index if not exists reports_reporter_idx on public.reports(reporter_id);
create index if not exists reports_target_user_idx on public.reports(target_user_id);
create index if not exists reputation_events_campaign_idx on public.reputation_events(campaign_id);
create index if not exists reputation_events_user_idx on public.reputation_events(user_id);
create index if not exists task_completions_tester_idx on public.task_completions(tester_id);
create index if not exists tester_groups_created_by_idx on public.tester_groups(created_by);

-- Replace broad FOR ALL self-management policies with explicit insert/update policies.
drop policy if exists "tester_profile_manage_self" on public.tester_profiles;
create policy "tester_profile_insert_self" on public.tester_profiles for insert to authenticated
with check ((select auth.uid()) = user_id);
create policy "tester_profile_update_self" on public.tester_profiles for update to authenticated
using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

drop policy if exists "developer_profile_manage_self" on public.developer_profiles;
create policy "developer_profile_insert_self" on public.developer_profiles for insert to authenticated
with check ((select auth.uid()) = user_id);
create policy "developer_profile_update_self" on public.developer_profiles for update to authenticated
using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

-- Keep participant reads and owner writes as separate policies.
drop policy if exists "tasks_manage_campaign_owner" on public.campaign_tasks;
create policy "tasks_insert_campaign_owner" on public.campaign_tasks for insert to authenticated
with check (exists (select 1 from public.campaigns c where c.id = campaign_id and c.developer_id = (select auth.uid())));
create policy "tasks_update_campaign_owner" on public.campaign_tasks for update to authenticated
using (exists (select 1 from public.campaigns c where c.id = campaign_id and c.developer_id = (select auth.uid())))
with check (exists (select 1 from public.campaigns c where c.id = campaign_id and c.developer_id = (select auth.uid())));
create policy "tasks_delete_campaign_owner" on public.campaign_tasks for delete to authenticated
using (exists (select 1 from public.campaigns c where c.id = campaign_id and c.developer_id = (select auth.uid())));

drop policy if exists "group_members_read_self" on public.group_members;
