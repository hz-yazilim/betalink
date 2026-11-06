-- Create a minimal profile whenever Supabase Auth creates a user.
-- The function is trigger-only; clients cannot call it directly.
create or replace function public.handle_new_betalink_user()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  insert into public.profiles (user_id, display_name, country_code)
  values (
    new.id,
    coalesce(nullif(new.raw_user_meta_data ->> 'display_name', ''), split_part(new.email, '@', 1)),
    'TR'
  )
  on conflict (user_id) do nothing;
  return new;
end;
$$;

revoke all on function public.handle_new_betalink_user() from public, anon, authenticated;

drop trigger if exists on_betalink_auth_user_created on auth.users;
create trigger on_betalink_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_betalink_user();
