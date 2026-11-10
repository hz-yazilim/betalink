alter table public.profiles
  add column if not exists bio text not null default '';

create table if not exists public.profile_contact_settings (
  user_id uuid primary key references auth.users(id) on delete cascade,
  contact_email text not null default '',
  show_public_email boolean not null default false,
  updated_at timestamptz not null default now(),
  constraint profile_contact_email_length check (char_length(contact_email) <= 254)
);

alter table public.profile_contact_settings enable row level security;
revoke all on public.profile_contact_settings from anon, authenticated;
grant select, insert, update on public.profile_contact_settings to authenticated;

drop policy if exists profile_contact_settings_select_own on public.profile_contact_settings;
create policy profile_contact_settings_select_own
  on public.profile_contact_settings for select to authenticated
  using (user_id = (select auth.uid()));

drop policy if exists profile_contact_settings_insert_own on public.profile_contact_settings;
create policy profile_contact_settings_insert_own
  on public.profile_contact_settings for insert to authenticated
  with check (user_id = (select auth.uid()));

drop policy if exists profile_contact_settings_update_own on public.profile_contact_settings;
create policy profile_contact_settings_update_own on public.profile_contact_settings for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

create or replace function private.betalink_public_contact_emails()
returns table(user_id uuid, contact_email text)
language plpgsql
security definer
set search_path = public, auth, private, pg_temp
as $$
begin
  if auth.uid() is null then
    raise exception 'Oturum gerekli.';
  end if;
  return query
    select s.user_id, s.contact_email
    from public.profile_contact_settings s
    where s.show_public_email = true
      and length(trim(s.contact_email)) > 0;
end;
$$;

create or replace function public.betalink_public_contact_emails()
returns table(user_id uuid, contact_email text)
language sql
security invoker
set search_path = public, private, pg_temp
as $$ select * from private.betalink_public_contact_emails(); $$;

revoke all on function private.betalink_public_contact_emails() from public, anon;
grant execute on function private.betalink_public_contact_emails() to authenticated;
revoke all on function public.betalink_public_contact_emails() from public, anon;
grant execute on function public.betalink_public_contact_emails() to authenticated;
