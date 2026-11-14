-- Private, campaign-scoped bug reports with optional screenshot attachments.
-- Attachments are stored under <auth.uid>/<campaign_id>/<random-id>.
create table if not exists public.bug_reports (
  id uuid primary key default gen_random_uuid(),
  campaign_id uuid not null references public.campaigns(id) on delete cascade,
  reporter_id uuid not null references auth.users(id) on delete cascade,
  description text not null check (char_length(description) between 10 and 5000),
  device_model text not null default '' check (char_length(device_model) <= 120),
  android_version text not null default '' check (char_length(android_version) <= 40),
  app_version text not null default '' check (char_length(app_version) <= 40),
  attachment_path text,
  status text not null default 'open' check (status in ('open','in_review','resolved','closed')),
  developer_reply text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint bug_report_attachment_path_matches_owner check (
    attachment_path is null or attachment_path like reporter_id::text || '/' || campaign_id::text || '/%'
  )
);

create index if not exists bug_reports_campaign_created_idx
  on public.bug_reports(campaign_id, created_at desc);
create index if not exists bug_reports_reporter_created_idx
  on public.bug_reports(reporter_id, created_at desc);

alter table public.bug_reports enable row level security;

drop policy if exists bug_reports_insert_as_campaign_tester on public.bug_reports;
create policy bug_reports_insert_as_campaign_tester
on public.bug_reports for insert to authenticated
with check (
  reporter_id = auth.uid()
  and exists (
    select 1 from public.campaign_members m
    where m.campaign_id = bug_reports.campaign_id
      and m.tester_id = auth.uid()
      and m.status in ('accepted','opted_in','active','completed')
  )
);

drop policy if exists bug_reports_read_reporter_or_campaign_owner on public.bug_reports;
create policy bug_reports_read_reporter_or_campaign_owner
on public.bug_reports for select to authenticated
using (
  reporter_id = auth.uid()
  or exists (
    select 1 from public.campaigns c
    where c.id = bug_reports.campaign_id and c.developer_id = auth.uid()
  )
);

grant select on public.bug_reports to authenticated;
grant insert (campaign_id, reporter_id, description, device_model, android_version, app_version, attachment_path)
on public.bug_reports to authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('bug-report-attachments', 'bug-report-attachments', false, 5242880, array['image/jpeg','image/png','image/webp'])
on conflict (id) do update set
  public = false,
  file_size_limit = 5242880,
  allowed_mime_types = array['image/jpeg','image/png','image/webp'];

drop policy if exists bug_report_attachment_upload on storage.objects;
create policy bug_report_attachment_upload
on storage.objects for insert to authenticated
with check (
  bucket_id = 'bug-report-attachments'
  and (storage.foldername(name))[1] = auth.uid()::text
  and exists (
    select 1 from public.campaign_members m
    where m.tester_id = auth.uid()
      and m.campaign_id::text = (storage.foldername(name))[2]
      and m.status in ('accepted','opted_in','active','completed')
  )
);

drop policy if exists bug_report_attachment_read on storage.objects;
create policy bug_report_attachment_read
on storage.objects for select to authenticated
using (
  bucket_id = 'bug-report-attachments'
  and (
    (storage.foldername(name))[1] = auth.uid()::text
    or exists (
      select 1 from public.campaigns c
      where c.id::text = (storage.foldername(name))[2]
        and c.developer_id = auth.uid()
    )
  )
);

drop policy if exists bug_report_attachment_delete on storage.objects;
create policy bug_report_attachment_delete
on storage.objects for delete to authenticated
using (
  bucket_id = 'bug-report-attachments'
  and (storage.foldername(name))[1] = auth.uid()::text
);
