-- A private group is visible only to its creator, approved members, and users
-- holding a pending invitation. SECURITY DEFINER avoids RLS recursion between
-- tester_groups, group_members, and group_invitations policies.
CREATE OR REPLACE FUNCTION private.betalink_can_view_group(p_group_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $fn$
  SELECT auth.uid() IS NOT NULL
     AND (
       EXISTS (
         SELECT 1 FROM public.group_members gm
         WHERE gm.group_id = p_group_id
           AND gm.user_id = auth.uid()
           AND gm.status = 'approved'
       )
       OR EXISTS (
         SELECT 1 FROM public.group_invitations gi
         WHERE gi.group_id = p_group_id
           AND gi.invitee_id = auth.uid()
           AND gi.status = 'pending'
       )
     );
$fn$;

REVOKE ALL ON FUNCTION private.betalink_can_view_group(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION private.betalink_can_view_group(uuid) TO authenticated;

DROP POLICY IF EXISTS groups_read_public_or_creator ON public.tester_groups;
CREATE POLICY groups_read_public_or_creator
ON public.tester_groups
FOR SELECT TO authenticated
USING (
  is_private = false
  OR created_by = (SELECT auth.uid())
  OR private.betalink_can_view_group(id)
);
