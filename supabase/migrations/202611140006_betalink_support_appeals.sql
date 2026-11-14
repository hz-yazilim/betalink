-- Explicit appeal ticket type; appeals use the existing private support workflow.
alter table public.support_tickets
  drop constraint if exists support_tickets_ticket_type_check;

alter table public.support_tickets
  add constraint support_tickets_ticket_type_check
  check (ticket_type in ('support', 'complaint', 'appeal'));
