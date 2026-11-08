-- Trigger-only completion summary functions must not be callable through PostgREST RPC.
-- PostgreSQL invokes trigger functions through their triggers; these grants are not needed.
revoke all on function public.betalink_notify_campaign_completion_summary() from public, anon, authenticated;
revoke all on function public.betalink_notify_tracking_completion_summary() from public, anon, authenticated;
