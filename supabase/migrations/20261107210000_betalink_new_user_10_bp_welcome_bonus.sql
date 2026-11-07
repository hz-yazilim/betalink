-- Give every newly registered Betalink account a one-time 10 BP welcome bonus.
-- Existing wallets and balances are intentionally left unchanged.
create or replace function public.handle_new_betalink_user()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
declare
  created_wallet_user_id uuid;
begin
  insert into public.profiles (user_id, display_name, country_code)
  values (
    new.id,
    coalesce(nullif(new.raw_user_meta_data ->> 'display_name', ''), split_part(new.email, '@', 1)),
    'TR'
  )
  on conflict (user_id) do nothing;

  insert into public.betalink_wallets (user_id, balance, lifetime_earned)
  values (new.id, 10, 10)
  on conflict (user_id) do nothing
  returning user_id into created_wallet_user_id;

  if created_wallet_user_id is not null then
    insert into public.betalink_wallet_ledger (user_id, amount, entry_type, description)
    values (new.id, 10, 'admin_adjustment', 'Hoş geldin bonusu: İlk kayıt 10 BP');
  end if;

  return new;
end;
$function$;
