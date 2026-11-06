create or replace function public.notify_betalink_support_ticket()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if tg_op = 'INSERT' then
    insert into public.notifications(recipient_id, notification_type, title, body)
    select user_id, 'support_ticket', 'Yeni destek talebi', 'Bir kullanıcı destek talebi oluşturdu.'
    from public.platform_admins;
  elsif old.admin_reply is distinct from new.admin_reply and new.admin_reply is not null then
    insert into public.notifications(recipient_id, notification_type, title, body)
    values (new.user_id, 'support_reply', 'Destek talebin yanıtlandı', new.subject);
  end if;
  return new;
end;
$$;
revoke all on function public.notify_betalink_support_ticket() from public, anon, authenticated;
drop trigger if exists on_betalink_support_ticket_notification on public.support_tickets;
create trigger on_betalink_support_ticket_notification
after insert or update of admin_reply on public.support_tickets
for each row execute function public.notify_betalink_support_ticket();
