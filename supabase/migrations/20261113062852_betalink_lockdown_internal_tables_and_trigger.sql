-- Internal control tables are service-role only; explicit deny policies provide defense in depth.
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['admin_audit_logs','campaign_tracking','platform_admins'] LOOP
    EXECUTE format('DROP POLICY IF EXISTS client_access_denied ON public.%I', t);
    EXECUTE format(
      'CREATE POLICY client_access_denied ON public.%I FOR ALL TO anon, authenticated USING (false) WITH CHECK (false)',
      t
    );
  END LOOP;
END
$$;

-- This is a trigger-only function, never a callable Data API endpoint.
REVOKE ALL ON FUNCTION public.guard_betalink_task_resubmission() FROM PUBLIC, anon, authenticated;
