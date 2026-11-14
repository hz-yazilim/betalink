-- Automatically remove campaign chat messages after two hours.
-- The cleanup runs in the database; no expiry notice is shown in the app.
create index if not exists campaign_messages_created_at_cleanup_idx
  on public.campaign_messages (created_at);

do $$
declare
  existing_job_id bigint;
begin
  select jobid into existing_job_id
  from cron.job
  where jobname = 'betalink-delete-expired-campaign-messages'
  limit 1;

  if existing_job_id is not null then
    perform cron.unschedule(existing_job_id);
  end if;

  perform cron.schedule(
    'betalink-delete-expired-campaign-messages',
    '* * * * *',
    $job$
      delete from public.campaign_messages
      where created_at < now() - interval '2 hours';
    $job$
  );
end
$$;
