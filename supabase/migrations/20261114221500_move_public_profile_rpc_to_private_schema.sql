alter function public.betalink_public_user_profile(uuid) set schema private;

create or replace function public.betalink_public_user_profile(p_user_id uuid)
returns table (
  user_id uuid,
  display_name text,
  bio text,
  avatar_url text,
  reputation_points integer,
  beta_points integer,
  tests_joined integer,
  tests_completed integer,
  tests_dropped integer,
  daily_checkins integer,
  missed_days integer,
  attendance_percent integer,
  same_group boolean
)
language sql
stable
security invoker
set search_path = ''
as $function$
  select * from private.betalink_public_user_profile(p_user_id);
$function$;

revoke all on function public.betalink_public_user_profile(uuid) from public;
revoke all on function public.betalink_public_user_profile(uuid) from anon;
grant execute on function public.betalink_public_user_profile(uuid) to authenticated;
grant execute on function private.betalink_public_user_profile(uuid) to authenticated;
