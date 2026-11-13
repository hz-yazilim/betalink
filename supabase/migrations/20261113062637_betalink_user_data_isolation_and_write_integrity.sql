-- Betalink production data isolation and write-integrity hardening.
-- No user rows are deleted or rewritten by this migration.

-- Anonymous Data API access is not part of the app: every app workflow requires a verified session.
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM anon;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated;

-- RLS remains the row boundary; remove bypass-like and unused table capabilities.
REVOKE TRUNCATE, REFERENCES, TRIGGER ON ALL TABLES IN SCHEMA public FROM authenticated;
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public FROM authenticated;

-- Fix two membership predicates that accidentally compared a column to itself.
DROP POLICY IF EXISTS tasks_read_campaign_participants_or_owner ON public.campaign_tasks;
CREATE POLICY tasks_read_campaign_participants_or_owner
ON public.campaign_tasks
FOR SELECT TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM public.campaigns c
    WHERE c.id = campaign_tasks.campaign_id
      AND c.developer_id = (SELECT auth.uid())
  )
  OR EXISTS (
    SELECT 1 FROM public.campaign_members m
    WHERE m.campaign_id = campaign_tasks.campaign_id
      AND m.tester_id = (SELECT auth.uid())
      AND m.status IN ('accepted','opted_in','active','completed')
  )
);

DROP POLICY IF EXISTS feedback_create_as_active_tester ON public.feedback;
CREATE POLICY feedback_create_as_active_tester
ON public.feedback
FOR INSERT TO authenticated
WITH CHECK (
  tester_id = (SELECT auth.uid())
  AND status = 'open'
  AND developer_reply IS NULL
  AND EXISTS (
    SELECT 1 FROM public.campaign_members m
    WHERE m.campaign_id = feedback.campaign_id
      AND m.tester_id = (SELECT auth.uid())
      AND m.status IN ('accepted','opted_in','active','completed')
  )
);

DROP POLICY IF EXISTS task_completions_insert_self ON public.task_completions;
CREATE POLICY task_completions_insert_self
ON public.task_completions
FOR INSERT TO authenticated
WITH CHECK (
  tester_id = (SELECT auth.uid())
  AND status = 'submitted'
  AND reviewed_at IS NULL
  AND EXISTS (
    SELECT 1
    FROM public.campaign_tasks t
    JOIN public.campaign_members m ON m.campaign_id = t.campaign_id
    WHERE t.id = task_completions.task_id
      AND m.tester_id = (SELECT auth.uid())
      AND m.status IN ('accepted','opted_in','active')
  )
);

-- Device and developer details are private to their owners.
DROP POLICY IF EXISTS tester_profile_read_authenticated ON public.tester_profiles;
DROP POLICY IF EXISTS tester_profile_read_self ON public.tester_profiles;
CREATE POLICY tester_profile_read_self
ON public.tester_profiles
FOR SELECT TO authenticated
USING (user_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS developer_profile_read_authenticated ON public.developer_profiles;
DROP POLICY IF EXISTS developer_profile_read_self ON public.developer_profiles;
CREATE POLICY developer_profile_read_self
ON public.developer_profiles
FOR SELECT TO authenticated
USING (user_id = (SELECT auth.uid()));

-- Reputation event history is private; the app can read only its own point entries.
DROP POLICY IF EXISTS reputation_read_authenticated ON public.reputation_events;
DROP POLICY IF EXISTS reputation_read_self ON public.reputation_events;
CREATE POLICY reputation_read_self
ON public.reputation_events
FOR SELECT TO authenticated
USING (user_id = (SELECT auth.uid()));

-- Keep community profile fields readable, but never expose terms acceptance metadata via the profiles table.
REVOKE ALL PRIVILEGES ON public.profiles FROM anon, authenticated;
GRANT SELECT (user_id, display_name, avatar_url, country_code, created_at, updated_at, allow_group_invites, bio)
ON public.profiles TO authenticated;
GRANT UPDATE (display_name, avatar_url, allow_group_invites, bio, updated_at)
ON public.profiles TO authenticated;

-- Keep tester device data self-only and limit writes to editable device fields.
REVOKE ALL PRIVILEGES ON public.tester_profiles FROM anon, authenticated;
GRANT SELECT ON public.tester_profiles TO authenticated;
GRANT INSERT (user_id, device_model, android_version, available_for_testing, updated_at)
ON public.tester_profiles TO authenticated;
GRANT UPDATE (device_model, android_version, available_for_testing, updated_at)
ON public.tester_profiles TO authenticated;

-- Developer profile rows are private to their owner.
REVOKE ALL PRIVILEGES ON public.developer_profiles FROM anon, authenticated;
GRANT SELECT ON public.developer_profiles TO authenticated;
GRANT INSERT (user_id, organization_name, website_url, updated_at)
ON public.developer_profiles TO authenticated;
GRANT UPDATE (organization_name, website_url, updated_at)
ON public.developer_profiles TO authenticated;

-- Never expose raw wallet tables; the owner-only wallet RPC returns the caller's balance.
REVOKE ALL PRIVILEGES ON public.betalink_wallets, public.betalink_wallet_ledger
FROM anon, authenticated;

-- Group creators can see membership metadata, but raw Play-test email addresses are returned
-- only by an owner-checked RPC and only for members who explicitly consented.
REVOKE ALL PRIVILEGES ON public.group_members FROM anon, authenticated;
GRANT SELECT (id, group_id, user_id, status, joined_at, share_test_email)
ON public.group_members TO authenticated;
GRANT UPDATE (status) ON public.group_members TO authenticated;

-- Reputation points are visible only to the owner; no client-side ledger writes.
REVOKE ALL PRIVILEGES ON public.reputation_events FROM anon, authenticated;
GRANT SELECT (user_id, points) ON public.reputation_events TO authenticated;

-- Regrant only the client writes used by the current app. RLS policies remain mandatory.
GRANT INSERT (campaign_id, tester_id, status, is_backup)
ON public.campaign_members TO authenticated;
GRANT UPDATE (status, is_backup)
ON public.campaign_members TO authenticated;

GRANT INSERT (campaign_id, tester_id, note)
ON public.campaign_daily_checkins TO authenticated;

GRANT INSERT (task_id, tester_id, notes, submitted_at)
ON public.task_completions TO authenticated;
GRANT UPDATE (status, notes, submitted_at, reviewed_at)
ON public.task_completions TO authenticated;

GRANT INSERT (campaign_id, tester_id, feedback_type, rating, body)
ON public.feedback TO authenticated;
GRANT UPDATE (developer_reply, status, updated_at)
ON public.feedback TO authenticated;

GRANT INSERT (reporter_id, target_user_id, campaign_id, reason, details)
ON public.reports TO authenticated;

GRANT INSERT (blocker_id, blocked_id)
ON public.blocked_users TO authenticated;
GRANT DELETE ON public.blocked_users TO authenticated;

GRANT INSERT (user_id, subject, message, ticket_type)
ON public.support_tickets TO authenticated;

GRANT UPDATE (read_at) ON public.notifications TO authenticated;

GRANT INSERT (user_id, contact_email, show_public_email, updated_at)
ON public.profile_contact_settings TO authenticated;
GRANT UPDATE (contact_email, show_public_email, updated_at)
ON public.profile_contact_settings TO authenticated;

-- Draft campaign deletion remains available only where the existing RLS policy permits it.
GRANT DELETE ON public.campaigns TO authenticated;

-- Views expose only safe summaries and are read-only for authenticated users.
CREATE OR REPLACE VIEW public.betalink_tester_reliability
WITH (security_invoker = true)
AS
SELECT
  p.user_id,
  p.display_name,
  (SELECT count(*) FROM public.campaign_members cm
    WHERE cm.tester_id = p.user_id
      AND cm.status IN ('completed','active','opted_in','accepted','dropped'))::integer AS tests_joined,
  (SELECT count(*) FROM public.campaign_members cm
    WHERE cm.tester_id = p.user_id AND cm.status = 'completed')::integer AS tests_completed,
  (SELECT count(*) FROM public.campaign_members cm
    WHERE cm.tester_id = p.user_id AND cm.status = 'dropped')::integer AS tests_dropped,
  ((SELECT count(*) FROM public.campaign_daily_checkins dc WHERE dc.tester_id = p.user_id)
   + (SELECT count(*) FROM public.group_daily_checkins gd WHERE gd.user_id = p.user_id))::integer AS daily_checkins,
  (SELECT coalesce(sum(re.points),0) FROM public.reputation_events re WHERE re.user_id = p.user_id)::integer AS reputation_points,
  ((SELECT coalesce(sum(cm.missed_days),0) FROM public.campaign_members cm WHERE cm.tester_id = p.user_id)
   + (SELECT coalesce(sum(gm.missed_days),0) FROM public.group_members gm WHERE gm.user_id = p.user_id))::integer AS missed_days
FROM public.profiles p
WHERE p.user_id = (SELECT auth.uid());

CREATE OR REPLACE VIEW public.betalink_campaign_report
WITH (security_invoker = true)
AS
SELECT
  c.id AS campaign_id,
  c.developer_id,
  c.app_name,
  c.target_testers,
  c.status,
  (SELECT count(*) FROM public.campaign_members cm
    WHERE cm.campaign_id = c.id AND cm.status IN ('active','opted_in','completed'))::integer AS active_testers,
  (SELECT count(*) FROM public.campaign_members cm
    WHERE cm.campaign_id = c.id AND cm.is_backup)::integer AS backup_testers,
  (SELECT count(*) FROM public.campaign_members cm
    WHERE cm.campaign_id = c.id AND cm.status = 'dropped')::integer AS dropped_testers,
  (SELECT count(*) FROM public.campaign_daily_checkins dc WHERE dc.campaign_id = c.id)::integer AS total_checkins,
  (SELECT count(*) FROM public.feedback f WHERE f.campaign_id = c.id)::integer AS feedback_count,
  (SELECT round(avg(f.rating),2) FROM public.feedback f WHERE f.campaign_id = c.id) AS average_rating,
  (SELECT count(*) FROM public.feedback f WHERE f.campaign_id = c.id AND f.feedback_type = 'bug')::integer AS bug_reports,
  (SELECT count(*) FROM public.feedback f WHERE f.campaign_id = c.id AND f.feedback_type = 'suggestion')::integer AS suggestions,
  (SELECT count(*) FROM public.campaign_members cm
    WHERE cm.campaign_id = c.id AND cm.status = 'accepted')::integer AS accepted_testers
FROM public.campaigns c
WHERE c.developer_id = (SELECT auth.uid());

REVOKE ALL PRIVILEGES ON public.betalink_tester_reliability, public.betalink_campaign_report
FROM anon, authenticated;
GRANT SELECT ON public.betalink_tester_reliability, public.betalink_campaign_report
TO authenticated;

-- Read current terms and accept them through narrowly scoped authenticated RPCs.
CREATE OR REPLACE FUNCTION private.betalink_current_terms()
RETURNS jsonb
LANGUAGE sql
SECURITY DEFINER
SET search_path = ''
AS $fn$
  SELECT coalesce(
    (SELECT pg_catalog.jsonb_build_object(
      'terms_version', p.terms_version,
      'terms_accepted_at', p.terms_accepted_at
    ) FROM public.profiles p WHERE p.user_id = auth.uid()),
    pg_catalog.jsonb_build_object('terms_version', NULL, 'terms_accepted_at', NULL)
  );
$fn$;

CREATE OR REPLACE FUNCTION public.betalink_current_terms()
RETURNS jsonb
LANGUAGE sql
SECURITY INVOKER
SET search_path = ''
AS $fn$
  SELECT private.betalink_current_terms();
$fn$;

CREATE OR REPLACE FUNCTION private.betalink_accept_current_terms(p_version text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $fn$
DECLARE uid uuid := auth.uid();
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'Oturum gerekli.'; END IF;
  IF p_version IS DISTINCT FROM '2026-11-08' THEN
    RAISE EXCEPTION 'Kabul edilen koşul sürümü güncel değil.';
  END IF;
  UPDATE public.profiles
  SET terms_version = p_version,
      terms_accepted_at = pg_catalog.now(),
      updated_at = pg_catalog.now()
  WHERE user_id = uid;
  IF NOT FOUND THEN RAISE EXCEPTION 'Kullanıcı profili bulunamadı.'; END IF;
  RETURN true;
END;
$fn$;

CREATE OR REPLACE FUNCTION public.betalink_accept_current_terms(p_version text)
RETURNS boolean
LANGUAGE sql
SECURITY INVOKER
SET search_path = ''
AS $fn$
  SELECT private.betalink_accept_current_terms(p_version);
$fn$;

REVOKE ALL ON FUNCTION private.betalink_current_terms() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION private.betalink_accept_current_terms(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION private.betalink_current_terms() TO authenticated;
GRANT EXECUTE ON FUNCTION private.betalink_accept_current_terms(text) TO authenticated;
REVOKE ALL ON FUNCTION public.betalink_current_terms() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.betalink_accept_current_terms(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.betalink_current_terms() TO authenticated;
GRANT EXECUTE ON FUNCTION public.betalink_accept_current_terms(text) TO authenticated;

-- Owner-only RPC for consented Google Play test email addresses in a group member list.
CREATE OR REPLACE FUNCTION private.betalink_group_member_test_emails(p_group_id uuid)
RETURNS TABLE(user_id uuid, display_name text, play_test_email text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $fn$
DECLARE uid uuid := auth.uid();
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'Oturum gerekli.'; END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.tester_groups g
    WHERE g.id = p_group_id AND g.created_by = uid
  ) THEN
    RAISE EXCEPTION 'Yalnızca grup kurucusu üye e-postalarını görebilir.';
  END IF;

  RETURN QUERY
  SELECT gm.user_id,
         coalesce(p.display_name, 'Betalink kullanıcısı'),
         gm.play_test_email
  FROM public.group_members gm
  LEFT JOIN public.profiles p ON p.user_id = gm.user_id
  WHERE gm.group_id = p_group_id
    AND gm.status = 'approved'
    AND gm.member_role = 'member'
    AND gm.share_test_email = true
    AND nullif(pg_catalog.btrim(gm.play_test_email), '') IS NOT NULL
  ORDER BY gm.joined_at ASC;
END;
$fn$;

CREATE OR REPLACE FUNCTION public.betalink_group_member_test_emails(p_group_id uuid)
RETURNS TABLE(user_id uuid, display_name text, play_test_email text)
LANGUAGE sql
SECURITY INVOKER
SET search_path = ''
AS $fn$
  SELECT * FROM private.betalink_group_member_test_emails(p_group_id);
$fn$;

REVOKE ALL ON FUNCTION private.betalink_group_member_test_emails(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION private.betalink_group_member_test_emails(uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.betalink_group_member_test_emails(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.betalink_group_member_test_emails(uuid) TO authenticated;
