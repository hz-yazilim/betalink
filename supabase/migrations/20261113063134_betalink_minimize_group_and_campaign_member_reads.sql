-- Restrict raw group and campaign membership reads to fields required by current screens.
REVOKE SELECT ON public.tester_groups FROM authenticated;
GRANT SELECT (id, name, description, is_private, created_by, created_at)
ON public.tester_groups TO authenticated;

REVOKE SELECT ON public.campaign_members FROM authenticated;
GRANT SELECT (id, campaign_id, tester_id, status, is_backup, joined_at)
ON public.campaign_members TO authenticated;

-- The public community card does not need country or internal update timestamps.
REVOKE SELECT (country_code, updated_at) ON public.profiles FROM authenticated;

-- Keep the tester's aggregate missed-day count available without exposing per-member
-- campaign_members.missed_days to campaign owners through direct table queries.
CREATE OR REPLACE FUNCTION private.betalink_my_campaign_missed_days()
RETURNS integer
LANGUAGE sql
SECURITY DEFINER
SET search_path = ''
AS $fn$
  SELECT coalesce(sum(cm.missed_days), 0)::integer
  FROM public.campaign_members cm
  WHERE cm.tester_id = auth.uid();
$fn$;

CREATE OR REPLACE FUNCTION public.betalink_my_campaign_missed_days()
RETURNS integer
LANGUAGE sql
SECURITY INVOKER
SET search_path = ''
AS $fn$
  SELECT private.betalink_my_campaign_missed_days();
$fn$;

REVOKE ALL ON FUNCTION private.betalink_my_campaign_missed_days() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION private.betalink_my_campaign_missed_days() TO authenticated;
REVOKE ALL ON FUNCTION public.betalink_my_campaign_missed_days() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.betalink_my_campaign_missed_days() TO authenticated;

CREATE OR REPLACE VIEW public.betalink_tester_reliability
WITH (security_invoker = true, security_barrier = true)
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
  (public.betalink_my_campaign_missed_days() + public.betalink_my_group_missed_days())::integer AS missed_days
FROM public.profiles p
WHERE p.user_id = (SELECT auth.uid());

REVOKE ALL PRIVILEGES ON public.betalink_tester_reliability FROM anon, authenticated;
GRANT SELECT ON public.betalink_tester_reliability TO authenticated;
