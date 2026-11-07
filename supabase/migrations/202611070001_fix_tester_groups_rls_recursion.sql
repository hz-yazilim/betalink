-- Break the circular RLS dependency between tester_groups and group_members.
-- Every tester group is public in the current Betalink product model; group membership
-- visibility is enforced by group_members policies independently.
drop policy if exists "groups_read_public_or_creator_or_member" on public.tester_groups;
create policy "groups_read_public_or_creator"
on public.tester_groups
for select
to authenticated
using (
  is_private = false
  or created_by = (select auth.uid())
);
