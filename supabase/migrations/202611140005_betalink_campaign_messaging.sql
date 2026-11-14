-- Private campaign room for the campaign owner and approved testers.
create table if not exists public.campaign_messages (
  id uuid primary key default gen_random_uuid(),
  campaign_id uuid not null references public.campaigns(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 1000),
  created_at timestamptz not null default now()
);

create index if not exists campaign_messages_campaign_created_idx
  on public.campaign_messages(campaign_id, created_at asc);

alter table public.campaign_messages enable row level security;

drop policy if exists campaign_messages_read_participants on public.campaign_messages;
create policy campaign_messages_read_participants
on public.campaign_messages for select to authenticated
using (
  sender_id = (select auth.uid())
  or exists (
    select 1 from public.campaigns c
    where c.id = campaign_messages.campaign_id
      and c.developer_id = (select auth.uid())
  )
  or exists (
    select 1 from public.campaign_members m
    where m.campaign_id = campaign_messages.campaign_id
      and m.tester_id = (select auth.uid())
      and m.status in ('accepted','opted_in','active','completed')
  )
);

drop policy if exists campaign_messages_insert_participants on public.campaign_messages;
create policy campaign_messages_insert_participants
on public.campaign_messages for insert to authenticated
with check (
  sender_id = (select auth.uid())
  and (
    exists (
      select 1 from public.campaigns c
      where c.id = campaign_messages.campaign_id
        and c.developer_id = (select auth.uid())
    )
    or exists (
      select 1 from public.campaign_members m
      where m.campaign_id = campaign_messages.campaign_id
        and m.tester_id = (select auth.uid())
        and m.status in ('accepted','opted_in','active','completed')
    )
  )
);

grant select on public.campaign_messages to authenticated;
grant insert (campaign_id, sender_id, body) on public.campaign_messages to authenticated;
