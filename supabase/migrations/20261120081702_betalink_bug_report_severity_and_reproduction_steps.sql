alter table public.bug_reports
  add column if not exists severity text not null default 'medium'
    check (severity in ('low','medium','high','critical')),
  add column if not exists reproduction_steps text not null default ''
    check (char_length(reproduction_steps) <= 5000);

grant insert (
  campaign_id, reporter_id, description, device_model, android_version,
  app_version, attachment_path, severity, reproduction_steps
) on public.bug_reports to authenticated;
