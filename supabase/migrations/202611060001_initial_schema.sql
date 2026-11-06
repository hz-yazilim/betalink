-- Betalink initial schema
-- Apply only to a dedicated Betalink Supabase project.
-- All public-schema tables use RLS. Admin-only operations are intentionally
-- reserved for trusted server-side code; never ship a service-role key in the APK.

create extension if not exists pgcrypto;

create table if not exists public.profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '',
  avatar_url text,
  country_code text not null default 'TR',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.tester_profiles (
  user_id uuid primary key references public.profiles(user_id) on delete cascade,
  device_model text,
  android_version integer check (android_version is null or android_version >= 1),
  play_country text,
  bio text,
  available_for_testing boolean not null default true,
  updated_at timestamptz not null default now()
);

create table if not exists public.developer_profiles (
  user_id uuid primary key references public.profiles(user_id) on delete cascade,
  organization_name text,
  website_url text,
  updated_at timestamptz not null default now()
);

create table if not exists public.campaigns (
  id uuid primary key default gen_random_uuid(),
  developer_id uuid not null references public.profiles(user_id) on delete cascade,
  app_name text not null check (char_length(app_name) between 2 and 100),
  play_opt_in_url text not null,
  description text not null default '',
  category text not null default 'other',
  target_testers integer not null default 12 check (target_testers between 1 and 1000),
  minimum_android_version integer check (minimum_android_version is null or minimum_android_version >= 1),
  required_device_notes text,
  start_date date,
  status text not null default 'draft' check (status in ('draft','open','paused','completed','cancelled')),
  acceptance_mode text not null default 'manual' check (acceptance_mode in ('manual','automatic')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint campaign_opt_in_https check (play_opt_in_url ~* '^https://')
);

-- This table is server-managed. It records the actual start/finish of the
-- independent campaign clock; a client cannot mark a campaign as completed.
create table if not exists public.campaign_tracking (
  campaign_id uuid primary key references public.campaigns(id) on delete cascade,
  minimum_active_testers integer not null default 12 check (minimum_active_testers between 1 and 1000),
  required_days integer not null default 14 check (required_days between 1 and 90),
  tracking_status text not null default 'not_started' check (tracking_status in ('not_started','running','eligible_for_review','completed','paused')),
  started_at timestamptz,
  completed_at timestamptz,
  updated_at timestamptz not null default now(),
  check ((tracking_status = 'not_started' and started_at is null) or (tracking_status <> 'not_started' and started_at is not null))
);

create table if not exists public.campaign_daily_checkins (
  id uuid primary key default gen_random_uuid(),
  campaign_id uuid not null references public.campaigns(id) on delete cascade,
  tester_id uuid not null references public.profiles(user_id) on delete cascade,
  checkin_date date not null default (now() at time zone 'utc')::date,
  confirmed_at timestamptz not null default now(),
  note text not null default '',
  unique (campaign_id, tester_id, checkin_date)
);

create table if not exists public.campaign_members (
  id uuid primary key default gen_random_uuid(),
  campaign_id uuid not null references public.campaigns(id) on delete cascade,
  tester_id uuid not null references public.profiles(user_id) on delete cascade,
  status text not null default 'applied' check (status in ('applied','accepted','waitlisted','opted_in','active','completed','withdrawn','rejected','dropped')),
  is_backup boolean not null default false,
  joined_at timestamptz not null default now(),
  opted_in_at timestamptz,
  last_confirmed_at timestamptz,
  unique (campaign_id, tester_id)
);

create table if not exists public.campaign_tasks (
  id uuid primary key default gen_random_uuid(),
  campaign_id uuid not null references public.campaigns(id) on delete cascade,
  title text not null check (char_length(title) between 2 and 120),
  description text not null default '',
  position integer not null default 0,
  is_required boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.task_completions (
  id uuid primary key default gen_random_uuid(),
  task_id uuid not null references public.campaign_tasks(id) on delete cascade,
  tester_id uuid not null references public.profiles(user_id) on delete cascade,
  status text not null default 'submitted' check (status in ('submitted','approved','needs_changes')),
  notes text not null default '',
  submitted_at timestamptz not null default now(),
  reviewed_at timestamptz,
  unique (task_id, tester_id)
);

create table if not exists public.feedback (
  id uuid primary key default gen_random_uuid(),
  campaign_id uuid not null references public.campaigns(id) on delete cascade,
  tester_id uuid not null references public.profiles(user_id) on delete cascade,
  feedback_type text not null default 'experience' check (feedback_type in ('bug','experience','suggestion')),
  rating integer check (rating is null or rating between 1 and 5),
  body text not null check (char_length(body) between 5 and 10000),
  developer_reply text,
  status text not null default 'open' check (status in ('open','resolved')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.tester_groups (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(name) between 2 and 80),
  description text not null default '',
  is_private boolean not null default false,
  created_by uuid not null references public.profiles(user_id) on delete cascade,
  created_at timestamptz not null default now()
);

create table if not exists public.group_members (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references public.tester_groups(id) on delete cascade,
  user_id uuid not null references public.profiles(user_id) on delete cascade,
  member_role text not null default 'member' check (member_role in ('leader','member')),
  status text not null default 'pending' check (status in ('pending','approved','invited','removed')),
  joined_at timestamptz not null default now(),
  unique (group_id, user_id)
);

create table if not exists public.notifications (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid not null references public.profiles(user_id) on delete cascade,
  notification_type text not null,
  title text not null,
  body text not null default '',
  read_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.profiles(user_id) on delete cascade,
  target_user_id uuid references public.profiles(user_id) on delete set null,
  campaign_id uuid references public.campaigns(id) on delete set null,
  reason text not null,
  details text not null default '',
  status text not null default 'pending' check (status in ('pending','reviewing','resolved','rejected')),
  created_at timestamptz not null default now()
);

create table if not exists public.blocked_users (
  blocker_id uuid not null references public.profiles(user_id) on delete cascade,
  blocked_id uuid not null references public.profiles(user_id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);

create table if not exists public.reputation_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(user_id) on delete cascade,
  campaign_id uuid references public.campaigns(id) on delete set null,
  event_type text not null,
  points integer not null default 0,
  reason text not null default '',
  created_at timestamptz not null default now()
);

create index if not exists campaigns_status_created_idx on public.campaigns(status, created_at desc);
create index if not exists campaigns_developer_idx on public.campaigns(developer_id);
create index if not exists campaign_members_tester_idx on public.campaign_members(tester_id, joined_at desc);
create index if not exists campaign_daily_checkins_campaign_date_idx on public.campaign_daily_checkins(campaign_id, checkin_date);
create index if not exists campaign_members_campaign_status_idx on public.campaign_members(campaign_id, status);
create index if not exists campaign_tasks_campaign_idx on public.campaign_tasks(campaign_id, position);
create index if not exists feedback_campaign_idx on public.feedback(campaign_id, created_at desc);
create index if not exists notifications_recipient_idx on public.notifications(recipient_id, created_at desc);

alter table public.profiles enable row level security;
alter table public.tester_profiles enable row level security;
alter table public.developer_profiles enable row level security;
alter table public.campaigns enable row level security;
alter table public.campaign_tracking enable row level security;
alter table public.campaign_daily_checkins enable row level security;
alter table public.campaign_members enable row level security;
alter table public.campaign_tasks enable row level security;
alter table public.task_completions enable row level security;
alter table public.feedback enable row level security;
alter table public.tester_groups enable row level security;
alter table public.group_members enable row level security;
alter table public.notifications enable row level security;
alter table public.reports enable row level security;
alter table public.blocked_users enable row level security;
alter table public.reputation_events enable row level security;

-- User-owned profile data.
create policy "profiles_read_authenticated" on public.profiles for select to authenticated using (true);
create policy "profiles_insert_self" on public.profiles for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "profiles_update_self" on public.profiles for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "tester_profile_read_authenticated" on public.tester_profiles for select to authenticated using (true);
create policy "tester_profile_manage_self" on public.tester_profiles for all to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "developer_profile_read_authenticated" on public.developer_profiles for select to authenticated using (true);
create policy "developer_profile_manage_self" on public.developer_profiles for all to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

-- Published campaigns are discoverable; drafts and management stay with the owner.
create policy "campaigns_read_open_or_owner" on public.campaigns for select to authenticated
using (status = 'open' or developer_id = (select auth.uid()));
create policy "campaigns_create_as_owner" on public.campaigns for insert to authenticated
with check (developer_id = (select auth.uid()));
create policy "campaigns_update_owner" on public.campaigns for update to authenticated
using (developer_id = (select auth.uid())) with check (developer_id = (select auth.uid()));
create policy "campaigns_delete_draft_owner" on public.campaigns for delete to authenticated
using (developer_id = (select auth.uid()) and status = 'draft');

-- Campaign owners manage applicants; testers can see and manage only their own membership.
create policy "members_read_self_or_campaign_owner" on public.campaign_members for select to authenticated
using (
  tester_id = (select auth.uid()) or exists (
    select 1 from public.campaigns c where c.id = campaign_id and c.developer_id = (select auth.uid())
  )
);
create policy "members_apply_self" on public.campaign_members for insert to authenticated
with check (
  tester_id = (select auth.uid())
  and status = 'applied'
  and is_backup = false
  and opted_in_at is null
  and exists (select 1 from public.campaigns c where c.id = campaign_id and c.status = 'open')
);
create policy "campaign_checkins_read_self_or_owner" on public.campaign_daily_checkins for select to authenticated
using (
  tester_id = (select auth.uid()) or exists (
    select 1 from public.campaigns c where c.id = campaign_id and c.developer_id = (select auth.uid())
  )
);
create policy "campaign_checkins_insert_self" on public.campaign_daily_checkins for insert to authenticated
with check (
  tester_id = (select auth.uid()) and exists (
    select 1 from public.campaign_members m
    where m.campaign_id = campaign_id and m.tester_id = (select auth.uid()) and m.status in ('accepted','opted_in','active')
  )
);

create policy "members_update_campaign_owner" on public.campaign_members for update to authenticated
using (exists (select 1 from public.campaigns c where c.id = campaign_id and c.developer_id = (select auth.uid())))
with check (exists (select 1 from public.campaigns c where c.id = campaign_id and c.developer_id = (select auth.uid())));

create policy "tasks_read_campaign_participants_or_owner" on public.campaign_tasks for select to authenticated
using (
  exists (select 1 from public.campaigns c where c.id = campaign_id and c.developer_id = (select auth.uid()))
  or exists (select 1 from public.campaign_members m where m.campaign_id = campaign_id and m.tester_id = (select auth.uid()) and m.status in ('accepted','opted_in','active','completed'))
);
create policy "tasks_manage_campaign_owner" on public.campaign_tasks for all to authenticated
using (exists (select 1 from public.campaigns c where c.id = campaign_id and c.developer_id = (select auth.uid())))
with check (exists (select 1 from public.campaigns c where c.id = campaign_id and c.developer_id = (select auth.uid())));

create policy "task_completions_read_self_or_owner" on public.task_completions for select to authenticated
using (
  tester_id = (select auth.uid()) or exists (
    select 1 from public.campaign_tasks t join public.campaigns c on c.id = t.campaign_id
    where t.id = task_id and c.developer_id = (select auth.uid())
  )
);
create policy "task_completions_insert_self" on public.task_completions for insert to authenticated
with check (
  tester_id = (select auth.uid()) and exists (
    select 1 from public.campaign_tasks t join public.campaign_members m on m.campaign_id = t.campaign_id
    where t.id = task_id and m.tester_id = (select auth.uid()) and m.status in ('accepted','opted_in','active')
  )
);
create policy "task_completions_update_campaign_owner" on public.task_completions for update to authenticated
using (exists (
  select 1 from public.campaign_tasks t join public.campaigns c on c.id = t.campaign_id
  where t.id = task_id and c.developer_id = (select auth.uid())
))
with check (exists (
  select 1 from public.campaign_tasks t join public.campaigns c on c.id = t.campaign_id
  where t.id = task_id and c.developer_id = (select auth.uid())
));

create policy "feedback_read_author_or_owner" on public.feedback for select to authenticated
using (
  tester_id = (select auth.uid()) or exists (
    select 1 from public.campaigns c where c.id = campaign_id and c.developer_id = (select auth.uid())
  )
);
create policy "feedback_create_as_active_tester" on public.feedback for insert to authenticated
with check (
  tester_id = (select auth.uid()) and exists (
    select 1 from public.campaign_members m where m.campaign_id = campaign_id and m.tester_id = (select auth.uid()) and m.status in ('accepted','opted_in','active','completed')
  )
);
create policy "feedback_update_owner" on public.feedback for update to authenticated
using (exists (select 1 from public.campaigns c where c.id = campaign_id and c.developer_id = (select auth.uid())))
with check (exists (select 1 from public.campaigns c where c.id = campaign_id and c.developer_id = (select auth.uid())));

-- Public groups can be discovered by signed-in users; private groups remain invitation-based.
create policy "groups_read_public_or_creator_or_member" on public.tester_groups for select to authenticated
using (not is_private or created_by = (select auth.uid()) or exists (
  select 1 from public.group_members gm where gm.group_id = id and gm.user_id = (select auth.uid()) and gm.status = 'approved'
));
create policy "groups_create_self" on public.tester_groups for insert to authenticated
with check (created_by = (select auth.uid()));
create policy "groups_update_creator" on public.tester_groups for update to authenticated
using (created_by = (select auth.uid())) with check (created_by = (select auth.uid()));
create policy "group_members_read_self" on public.group_members for select to authenticated
using (user_id = (select auth.uid()));
create policy "group_members_join_self" on public.group_members for insert to authenticated
with check (
  user_id = (select auth.uid())
  and member_role = 'member'
  and exists (
    select 1 from public.tester_groups g
    where g.id = group_id
      and ((g.is_private and status = 'pending') or (not g.is_private and status = 'approved'))
  )
);

create policy "notifications_read_self" on public.notifications for select to authenticated using (recipient_id = (select auth.uid()));
create policy "notifications_update_self" on public.notifications for update to authenticated using (recipient_id = (select auth.uid())) with check (recipient_id = (select auth.uid()));
create policy "reports_create_self" on public.reports for insert to authenticated with check (reporter_id = (select auth.uid()));
create policy "reports_read_reporter" on public.reports for select to authenticated using (reporter_id = (select auth.uid()));
create policy "blocks_manage_self" on public.blocked_users for all to authenticated using (blocker_id = (select auth.uid())) with check (blocker_id = (select auth.uid()));
create policy "reputation_read_authenticated" on public.reputation_events for select to authenticated using (true);

grant usage on schema public to authenticated;
grant select, insert, update, delete on public.profiles to authenticated;
grant select, insert, update, delete on public.tester_profiles to authenticated;
grant select, insert, update, delete on public.developer_profiles to authenticated;
grant select, insert, update, delete on public.campaigns to authenticated;
grant select, insert, update, delete on public.campaign_members to authenticated;
grant select, insert on public.campaign_daily_checkins to authenticated;
grant select, insert, update, delete on public.campaign_tasks to authenticated;
grant select, insert, update, delete on public.task_completions to authenticated;
revoke update on public.feedback from authenticated;
grant select, insert on public.feedback to authenticated;
grant update (developer_reply, status) on public.feedback to authenticated;
grant select, insert, update, delete on public.tester_groups to authenticated;
grant select, insert, update, delete on public.group_members to authenticated;
grant select, update on public.notifications to authenticated;
grant select, insert on public.reports to authenticated;
grant select, insert, delete on public.blocked_users to authenticated;
grant select on public.reputation_events to authenticated;
-- campaign_tracking intentionally has no authenticated grants or policies.

