-- Move privileged SECURITY DEFINER implementations out of the exposed Data API schema.
alter function public.betalink_campaign_feedback_summary(uuid) set schema private;
alter function public.betalink_group_daily_check_in(uuid,text) set schema private;
alter function public.betalink_group_remove_member(uuid,uuid) set schema private;
alter function public.betalink_tester_reliability_for_campaign(uuid,uuid) set schema private;
alter function public.betalink_process_campaign_attendance(uuid) set schema private;

grant usage on schema private to authenticated, service_role;
revoke all on function private.betalink_campaign_feedback_summary(uuid) from public,anon;
revoke all on function private.betalink_group_daily_check_in(uuid,text) from public,anon;
revoke all on function private.betalink_group_remove_member(uuid,uuid) from public,anon;
revoke all on function private.betalink_tester_reliability_for_campaign(uuid,uuid) from public,anon;
revoke all on function private.betalink_process_campaign_attendance(uuid) from public,anon;
grant execute on function private.betalink_campaign_feedback_summary(uuid) to authenticated;
grant execute on function private.betalink_group_daily_check_in(uuid,text) to authenticated;
grant execute on function private.betalink_group_remove_member(uuid,uuid) to authenticated;
grant execute on function private.betalink_tester_reliability_for_campaign(uuid,uuid) to authenticated;
grant execute on function private.betalink_process_campaign_attendance(uuid) to authenticated,service_role;

create or replace function public.betalink_campaign_feedback_summary(p_campaign_id uuid)
returns jsonb language sql security invoker set search_path = ''
as $fn$ select private.betalink_campaign_feedback_summary(p_campaign_id); $fn$;
create or replace function public.betalink_group_daily_check_in(p_group_id uuid,p_note text default 'Uygulama içi günlük grup testi bildirimi')
returns boolean language sql security invoker set search_path = ''
as $fn$ select private.betalink_group_daily_check_in(p_group_id,p_note); $fn$;
create or replace function public.betalink_group_remove_member(p_group_id uuid,p_member_id uuid)
returns boolean language sql security invoker set search_path = ''
as $fn$ select private.betalink_group_remove_member(p_group_id,p_member_id); $fn$;
create or replace function public.betalink_tester_reliability_for_campaign(p_campaign_id uuid,p_tester_id uuid)
returns jsonb language sql security invoker set search_path = ''
as $fn$ select private.betalink_tester_reliability_for_campaign(p_campaign_id,p_tester_id); $fn$;
create or replace function public.betalink_process_campaign_attendance(p_campaign_id uuid)
returns jsonb language sql security invoker set search_path = ''
as $fn$ select private.betalink_process_campaign_attendance(p_campaign_id); $fn$;

revoke all on function public.betalink_campaign_feedback_summary(uuid) from public,anon;
revoke all on function public.betalink_group_daily_check_in(uuid,text) from public,anon;
revoke all on function public.betalink_group_remove_member(uuid,uuid) from public,anon;
revoke all on function public.betalink_tester_reliability_for_campaign(uuid,uuid) from public,anon;
revoke all on function public.betalink_process_campaign_attendance(uuid) from public,anon;
grant execute on function public.betalink_campaign_feedback_summary(uuid) to authenticated;
grant execute on function public.betalink_group_daily_check_in(uuid,text) to authenticated;
grant execute on function public.betalink_group_remove_member(uuid,uuid) to authenticated;
grant execute on function public.betalink_tester_reliability_for_campaign(uuid,uuid) to authenticated;
grant execute on function public.betalink_process_campaign_attendance(uuid) to authenticated,service_role;
