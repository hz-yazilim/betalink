-- Campaign baseline tasks and independent continuous tester-count clock.
alter table public.campaign_tracking
  drop constraint if exists campaign_tracking_check;
alter table public.campaign_tracking
  add constraint campaign_tracking_started_at_state_check
  check (
    (tracking_status in ('not_started','paused') and started_at is null)
    or (tracking_status in ('running','eligible_for_review','completed') and started_at is not null)
  );

create or replace function public.create_betalink_campaign_tracking()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  insert into public.campaign_tracking (campaign_id, minimum_active_testers, required_days)
  values (new.id, 12, 14)
  on conflict (campaign_id) do nothing;

  insert into public.campaign_tasks (campaign_id, title, description, position, is_required)
  values
    (new.id, 'Kurulum ve ilk açılış', 'Google Play kapalı test bağlantısından uygulamayı yükle ve ilk açılışı tamamla.', 1, true),
    (new.id, 'Temel özellikleri dene', 'Uygulamanın açıklamasında belirtilen temel akışları gerçek kullanımda dene.', 2, true),
    (new.id, 'Geri bildirim gönder', 'Karşılaştığın hataları ve genel deneyimini dürüstçe bildir.', 3, true)
  on conflict do nothing;

  return new;
end;
$$;

create or replace function public.recalculate_betalink_campaign_clock()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  affected_campaign uuid;
  active_count integer;
  required_count integer;
  current_status text;
begin
  affected_campaign := case when tg_op = 'DELETE' then old.campaign_id else new.campaign_id end;

  select minimum_active_testers, tracking_status
    into required_count, current_status
    from public.campaign_tracking
    where campaign_id = affected_campaign
    for update;

  if not found or current_status in ('completed','eligible_for_review') then
    return coalesce(new, old);
  end if;

  select count(*)::integer into active_count
    from public.campaign_members
    where campaign_id = affected_campaign
      and status in ('opted_in','active')
      and opted_in_at is not null;

  if active_count >= required_count and current_status <> 'running' then
    update public.campaign_tracking
      set tracking_status = 'running', started_at = now(), completed_at = null, updated_at = now()
      where campaign_id = affected_campaign;
  elsif active_count < required_count and current_status = 'running' then
    update public.campaign_tracking
      set tracking_status = 'paused', started_at = null, updated_at = now()
      where campaign_id = affected_campaign;
  end if;

  return coalesce(new, old);
end;
$$;

revoke all on function public.create_betalink_campaign_tracking() from public, anon, authenticated;
revoke all on function public.recalculate_betalink_campaign_clock() from public, anon, authenticated;

drop trigger if exists on_betalink_campaign_created on public.campaigns;
create trigger on_betalink_campaign_created
after insert on public.campaigns
for each row execute function public.create_betalink_campaign_tracking();

drop trigger if exists on_betalink_member_clock_change on public.campaign_members;
create trigger on_betalink_member_clock_change
after insert or update of status, opted_in_at or delete on public.campaign_members
for each row execute function public.recalculate_betalink_campaign_clock();
