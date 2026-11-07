alter table public.support_tickets
  add column if not exists ticket_type text not null default 'support';

alter table public.support_tickets
  drop constraint if exists support_tickets_ticket_type_check;

alter table public.support_tickets
  add constraint support_tickets_ticket_type_check
  check (ticket_type in ('support', 'complaint'));
