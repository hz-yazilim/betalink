create table if not exists public.support_tickets (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(user_id) on delete cascade,
  subject text not null check (char_length(subject) between 3 and 120),
  message text not null check (char_length(message) between 5 and 5000),
  status text not null default 'open' check (status in ('open','in_progress','resolved')),
  admin_reply text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.support_tickets enable row level security;
drop policy if exists "support_tickets_read_self" on public.support_tickets;
create policy "support_tickets_read_self" on public.support_tickets for select to authenticated
using (user_id = (select auth.uid()));
drop policy if exists "support_tickets_insert_self" on public.support_tickets;
create policy "support_tickets_insert_self" on public.support_tickets for insert to authenticated
with check (user_id = (select auth.uid()));
create index if not exists support_tickets_user_created_idx on public.support_tickets(user_id, created_at desc);
