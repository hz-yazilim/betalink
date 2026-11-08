create or replace function public.betalink_process_all_attendance()
returns jsonb language plpgsql security definer set search_path=public as $fn$
declare campaigns integer; groups integer;
begin
 if current_user not in ('postgres','service_role','supabase_admin') then raise exception 'Sistem yetkisi gerekli.'; end if;
 campaigns:=public.betalink_process_all_campaign_attendance();
 groups:=public.betalink_process_all_group_attendance();
 return jsonb_build_object('campaigns_checked',campaigns,'group_members_checked',groups);
end $fn$;
revoke all on function public.betalink_process_all_attendance() from public,anon,authenticated;
do $cron$
begin
 if exists(select 1 from pg_extension where extname='pg_cron') then
  execute 'select cron.unschedule(jobid) from cron.job where jobname = ''betalink-daily-attendance''';
  execute 'select cron.schedule(''betalink-daily-attendance'',''15 9 * * *'',''select public.betalink_process_all_attendance();'')';
 end if;
end
$cron$;