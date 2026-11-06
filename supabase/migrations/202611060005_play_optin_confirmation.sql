-- Testers explicitly attest that they joined the Google Play closed test.
-- This is a user declaration, not independent Play Console verification.
create or replace function public.confirm_betalink_play_opt_in(p_campaign_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;

  update public.campaign_members
     set status = 'opted_in',
         opted_in_at = coalesce(opted_in_at, now()),
         last_confirmed_at = now()
   where campaign_id = p_campaign_id
     and tester_id = auth.uid()
     and status in ('accepted','opted_in','active');

  if not found then
    raise exception 'No accepted application found for this campaign' using errcode = 'P0002';
  end if;
end;
$$;

revoke all on function public.confirm_betalink_play_opt_in(uuid) from public, anon;
grant execute on function public.confirm_betalink_play_opt_in(uuid) to authenticated;
