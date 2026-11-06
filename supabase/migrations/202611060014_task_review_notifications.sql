create or replace function public.notify_betalink_task_submission()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare owner_id uuid; app_label text; task_label text;
begin
  select c.developer_id, c.app_name, t.title
    into owner_id, app_label, task_label
  from public.campaign_tasks t
  join public.campaigns c on c.id = t.campaign_id
  where t.id = new.task_id;

  if tg_op = 'INSERT' then
    if owner_id is not null and owner_id <> new.tester_id then
      insert into public.notifications(recipient_id, notification_type, title, body)
      values (owner_id, 'task_submission', 'Yeni görev teslimi', app_label || ' · ' || task_label);
    end if;
  elsif old.status is distinct from new.status then
    insert into public.notifications(recipient_id, notification_type, title, body)
    values (
      new.tester_id,
      'task_review',
      case when new.status = 'approved' then 'Görevin onaylandı' else 'Görevinde düzenleme istendi' end,
      app_label || ' · ' || task_label
    );
  end if;
  return new;
end;
$$;
revoke all on function public.notify_betalink_task_submission() from public, anon, authenticated;
drop trigger if exists on_betalink_task_submission_notification on public.task_completions;
create trigger on_betalink_task_submission_notification
after insert or update of status on public.task_completions
for each row execute function public.notify_betalink_task_submission();
