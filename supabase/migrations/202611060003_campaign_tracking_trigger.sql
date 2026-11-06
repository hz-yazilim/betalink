-- Ensure every new campaign gets its own independent 14-day tracking row.
create or replace function public.create_betalink_campaign_tracking()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  insert into public.campaign_tracking (campaign_id, minimum_active_testers, required_days)
  values (new.id, least(new.target_testers, 12), 14)
  on conflict (campaign_id) do nothing;
  return new;
end;
$$;

revoke all on function public.create_betalink_campaign_tracking() from public, anon, authenticated;

drop trigger if exists on_betalink_campaign_created on public.campaigns;
create trigger on_betalink_campaign_created
after insert on public.campaigns
for each row execute function public.create_betalink_campaign_tracking();
