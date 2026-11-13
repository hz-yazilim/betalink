-- Keep the reliability view SECURITY INVOKER while calculating the caller's own
-- group absence total through a narrowly scoped helper.
CREATE OR REPLACE FUNCTION private.betalink_my_group_missed_days()
RETURNS integer
LANGUAGE sql
SECURITY DEFINER
SET search_path = ''
AS $fn$
  SELECT coalesce(sum(gm.missed_days), 0)::integer
  FROM public.group_members gm
  WHERE gm.user_id = auth.uid();
$fn$;

CREATE OR REPLACE FUNCTION public.betalink_my_group_missed_days()
RETURNS integer
LANGUAGE sql
SECURITY INVOKER
SET search_path = ''
AS $fn$
  SELECT private.betalink_my_group_missed_days();
$fn$;

REVOKE ALL ON FUNCTION private.betalink_my_group_missed_days() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION private.betalink_my_group_missed_days() TO authenticated;
REVOKE ALL ON FUNCTION public.betalink_my_group_missed_days() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.betalink_my_group_missed_days() TO authenticated;

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
  ((SELECT coalesce(sum(cm.missed_days),0) FROM public.campaign_members cm WHERE cm.tester_id = p.user_id)
   + public.betalink_my_group_missed_days())::integer AS missed_days
FROM public.profiles p
WHERE p.user_id = (SELECT auth.uid());

REVOKE ALL PRIVILEGES ON public.betalink_tester_reliability FROM anon, authenticated;
GRANT SELECT ON public.betalink_tester_reliability TO authenticated;
