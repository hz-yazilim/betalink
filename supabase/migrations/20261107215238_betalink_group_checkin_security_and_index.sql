-- Restrict the per-user group check-in RPC to signed-in members only.
-- The function intentionally remains SECURITY DEFINER because the check-in table
-- is not directly writable by clients; the function validates auth.uid() and
-- requires an approved membership before inserting.
REVOKE EXECUTE ON FUNCTION public.betalink_group_daily_check_in(uuid, text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.betalink_group_daily_check_in(uuid, text) FROM anon;
GRANT EXECUTE ON FUNCTION public.betalink_group_daily_check_in(uuid, text) TO authenticated;

-- Supports the per-user history query and foreign-key maintenance.
CREATE INDEX IF NOT EXISTS group_daily_checkins_user_id_idx
  ON public.group_daily_checkins (user_id);
