drop policy if exists "group_members_read_self_or_group_creator" on public.group_members;
create policy "group_members_read_self_or_group_creator"
on public.group_members for select to authenticated
using (
  user_id = (select auth.uid())
  or exists (
    select 1 from public.tester_groups g
    where g.id = group_id and g.created_by = (select auth.uid())
  )
);

drop policy if exists "group_members_manage_group_creator" on public.group_members;
create policy "group_members_manage_group_creator"
on public.group_members for update to authenticated
using (
  exists (select 1 from public.tester_groups g where g.id = group_id and g.created_by = (select auth.uid()))
)
with check (
  exists (select 1 from public.tester_groups g where g.id = group_id and g.created_by = (select auth.uid()))
);

create or replace function public.add_betalink_group_creator_as_leader()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  insert into public.group_members (group_id, user_id, member_role, status)
  values (new.id, new.created_by, 'leader', 'approved')
  on conflict (group_id, user_id) do nothing;
  return new;
end;
$$;

revoke all on function public.add_betalink_group_creator_as_leader() from public, anon, authenticated;
drop trigger if exists on_betalink_group_created on public.tester_groups;
create trigger on_betalink_group_created
after insert on public.tester_groups
for each row execute function public.add_betalink_group_creator_as_leader();
