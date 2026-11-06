create or replace function public.notify_betalink_group_request()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare owner_id uuid; group_label text;
begin
  select created_by, name into owner_id, group_label from public.tester_groups where id = new.group_id;
  if tg_op = 'INSERT' and new.status = 'pending' and owner_id is not null and owner_id <> new.user_id then
    insert into public.notifications(recipient_id, notification_type, title, body)
    values (owner_id, 'group_request', 'Yeni grup katılım isteği', group_label || ' grubuna katılım isteği geldi.');
  elsif tg_op = 'UPDATE' and old.status is distinct from new.status then
    insert into public.notifications(recipient_id, notification_type, title, body)
    values (new.user_id, 'group_membership_status', 'Grup isteğin güncellendi', group_label || ' grubundaki üyelik durumun: ' || new.status);
  end if;
  return new;
end;
$$;
revoke all on function public.notify_betalink_group_request() from public, anon, authenticated;
drop trigger if exists on_betalink_group_request_notification on public.group_members;
create trigger on_betalink_group_request_notification
after insert or update of status on public.group_members
for each row execute function public.notify_betalink_group_request();
