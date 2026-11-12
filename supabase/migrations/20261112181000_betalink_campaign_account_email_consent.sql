-- Explicit account-Gmail consent for campaign applicants and developer-only visibility.
alter table public.campaign_members
  add column if not exists share_account_email boolean not null default false;

create or replace function private.betalink_campaign_set_account_email_consent(p_campaign_id uuid,p_share boolean)
returns boolean language plpgsql security definer set search_path=public,auth,private,pg_temp
as $$
declare uid uuid:=auth.uid(); affected integer;
begin
  if uid is null then raise exception 'Oturum gerekli.'; end if;
  if p_share is null then raise exception 'Paylaşım tercihi gerekli.'; end if;
  update public.campaign_members
    set share_account_email=p_share
    where campaign_id=p_campaign_id and tester_id=uid;
  get diagnostics affected=row_count;
  if affected=0 then raise exception 'Bu kampanyaya başvurun bulunmuyor.'; end if;
  return true;
end;
$$;

create or replace function public.betalink_campaign_set_account_email_consent(p_campaign_id uuid,p_share boolean)
returns boolean language sql security invoker set search_path=public,private,pg_temp
as $$ select private.betalink_campaign_set_account_email_consent(p_campaign_id,p_share); $$;

create or replace function private.betalink_campaign_account_email_consent(p_campaign_id uuid)
returns boolean language sql security definer set search_path=public,auth,private,pg_temp
as $$
  select coalesce((
    select cm.share_account_email from public.campaign_members cm
    where cm.campaign_id=p_campaign_id and cm.tester_id=auth.uid()
  ),false);
$$;

create or replace function public.betalink_campaign_account_email_consent(p_campaign_id uuid)
returns boolean language sql security invoker set search_path=public,private,pg_temp
as $$ select private.betalink_campaign_account_email_consent(p_campaign_id); $$;

create or replace function private.betalink_campaign_member_account_emails(p_campaign_id uuid)
returns table(tester_id uuid,display_name text,account_email text)
language plpgsql security definer set search_path=public,auth,private,pg_temp
as $$
declare uid uuid:=auth.uid(); owner_id uuid;
begin
  if uid is null then raise exception 'Oturum gerekli.'; end if;
  select c.developer_id into owner_id from public.campaigns c where c.id=p_campaign_id;
  if owner_id is distinct from uid then raise exception 'Yalnızca kampanya sahibi tester hesap e-postalarını görebilir.'; end if;
  return query
    select cm.tester_id,coalesce(p.display_name,'Betalink kullanıcısı'),coalesce(u.email,'')
    from public.campaign_members cm
    join auth.users u on u.id=cm.tester_id
    left join public.profiles p on p.user_id=cm.tester_id
    where cm.campaign_id=p_campaign_id and cm.share_account_email=true
    order by cm.joined_at asc;
end;
$$;

create or replace function public.betalink_campaign_member_account_emails(p_campaign_id uuid)
returns table(tester_id uuid,display_name text,account_email text)
language sql security invoker set search_path=public,private,pg_temp
as $$ select * from private.betalink_campaign_member_account_emails(p_campaign_id); $$;

revoke all on function private.betalink_campaign_set_account_email_consent(uuid,boolean) from public,anon;
revoke all on function private.betalink_campaign_account_email_consent(uuid) from public,anon;
revoke all on function private.betalink_campaign_member_account_emails(uuid) from public,anon;
grant execute on function private.betalink_campaign_set_account_email_consent(uuid,boolean) to authenticated;
grant execute on function private.betalink_campaign_account_email_consent(uuid) to authenticated;
grant execute on function private.betalink_campaign_member_account_emails(uuid) to authenticated;
revoke all on function public.betalink_campaign_set_account_email_consent(uuid,boolean) from public,anon;
revoke all on function public.betalink_campaign_account_email_consent(uuid) from public,anon;
revoke all on function public.betalink_campaign_member_account_emails(uuid) from public,anon;
grant execute on function public.betalink_campaign_set_account_email_consent(uuid,boolean) to authenticated;
grant execute on function public.betalink_campaign_account_email_consent(uuid) to authenticated;
grant execute on function public.betalink_campaign_member_account_emails(uuid) to authenticated;
