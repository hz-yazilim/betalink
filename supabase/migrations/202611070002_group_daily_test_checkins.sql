create table if not exists public.group_daily_checkins (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references public.tester_groups(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  checkin_date date not null default ((now() at time zone 'utc')::date),
  confirmed_at timestamptz not null default now(),
  note text not null default 'Uygulama içi günlük grup testi bildirimi',
  constraint group_daily_checkins_once_per_day unique (group_id, user_id, checkin_date)
);

alter table public.group_daily_checkins enable row level security;

drop policy if exists group_daily_checkins_read_self on public.group_daily_checkins;
create policy group_daily_checkins_read_self
on public.group_daily_checkins for select to authenticated
using (user_id = (select auth.uid()));

grant select on public.group_daily_checkins to authenticated;

create or replace function public.betalink_group_daily_check_in(
  p_group_id uuid,
  p_note text default 'Uygulama içi günlük grup testi bildirimi'
)
returns boolean
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
declare
  inserted_count integer;
begin
  if auth.uid() is null then
    raise exception 'Oturum gerekli.';
  end if;

  if not exists (
    select 1
    from public.group_members gm
    where gm.group_id = p_group_id
      and gm.user_id = auth.uid()
      and gm.status = 'approved'
  ) then
    raise exception 'Bu grubun onaylı üyesi değilsin.';
  end if;

  insert into public.group_daily_checkins (group_id, user_id, note)
  values (p_group_id, auth.uid(), coalesce(nullif(trim(p_note), ''), 'Günlük test bildirimi'))
  on conflict (group_id, user_id, checkin_date) do nothing;

  get diagnostics inserted_count = row_count;
  return inserted_count = 1;
end;
$function$;

revoke all on function public.betalink_group_daily_check_in(uuid, text) from public;
grant execute on function public.betalink_group_daily_check_in(uuid, text) to authenticated;
