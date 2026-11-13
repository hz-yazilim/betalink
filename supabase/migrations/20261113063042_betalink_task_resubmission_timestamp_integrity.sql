-- A tester can resubmit only as pending review; they cannot set review metadata.
DROP POLICY IF EXISTS task_completions_update_owner_or_resubmit ON public.task_completions;
CREATE POLICY task_completions_update_owner_or_resubmit
ON public.task_completions
FOR UPDATE TO authenticated
USING (
  (tester_id = (SELECT auth.uid()) AND status = 'needs_changes')
  OR EXISTS (
    SELECT 1
    FROM public.campaign_tasks t
    JOIN public.campaigns c ON c.id = t.campaign_id
    WHERE t.id = task_completions.task_id
      AND c.developer_id = (SELECT auth.uid())
  )
)
WITH CHECK (
  (tester_id = (SELECT auth.uid()) AND status = 'submitted' AND reviewed_at IS NULL)
  OR EXISTS (
    SELECT 1
    FROM public.campaign_tasks t
    JOIN public.campaigns c ON c.id = t.campaign_id
    WHERE t.id = task_completions.task_id
      AND c.developer_id = (SELECT auth.uid())
  )
);

-- New submissions receive their timestamp from the database default, not the client.
REVOKE INSERT (submitted_at) ON TABLE public.task_completions FROM authenticated;
