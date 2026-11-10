-- Betalink people directory, opt-out preferences, and secure group invitations.
alter table public.profiles
  add column if not exists allow_group_invites boolean not null default true;

create table if not exists public.group_invitations (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references public.tester_groups(id) on delete cascade,
  inviter_id uuid not null references auth.users(id) on delete cascade,
  invitee_id uuid not null references auth.users(id) on delete cascade,
  custom_message text not null default '',
  invite_token text not null unique default replace(gen_random_uuid()::text, '-', ''),
  status text not null default 'pending' check (status in ('pending','accepted','declined','revoked','expired')),
  created_at timestamptz not null default now(),
  responded_at timestamptz,
  constraint group_invitations_no_self check (inviter_id <> invitee_id),
  constraint group_invitations_unique_pending unique (group_id, invitee_id, status)
);
create index if not exists group_invitations_inbox_idx on public.group_invitations(invitee_id,status,created_at desc);
create index if not exists group_invitations_sender_idx on public.group_invitations(inviter_id,created_at desc);
alter table public.group_invitations enable row level security;
revoke all on public.group_invitations from anon,authenticated;
grant select on public.group_invitations to authenticated;
drop policy if exists group_invitations_read_participants on public.group_invitations;
create policy group_invitations_read_participants on public.group_invitations for select to authenticated
using (inviter_id=(select auth.uid()) or invitee_id=(select auth.uid()));

create or replace function private.betalink_send_group_invitation(p_group_id uuid,p_invitee_id uuid,p_message text default '')
returns jsonb language plpgsql security definer
set search_path=public,auth,private,pg_temp as $$
declare v_uid uuid:=auth.uid(); v_group public.tester_groups%rowtype; v_name text;
v_invitation public.group_invitations%rowtype; v_count integer;
begin
 if v_uid is null then raise exception 'Oturum gerekli.'; end if;
 if p_invitee_id is null or p_invitee_id=v_uid then raise exception 'Geçerli bir kişi seç.'; end if;
 select * into v_group from public.tester_groups where id=p_group_id for update;
 if not found then raise exception 'Grup bulunamadı.'; end if;
 if v_group.created_by<>v_uid then raise exception 'Yalnızca kendi grubuna davet gönderebilirsin.'; end if;
 if not exists(select 1 from public.profiles p where p.user_id=p_invitee_id and p.allow_group_invites=true) then
  raise exception 'Bu kullanıcı grup davetlerini kapatmış veya bulunamadı.';
 end if;
 if exists(select 1 from public.blocked_users b where
  (b.blocker_id=v_uid and b.blocked_id=p_invitee_id) or (b.blocker_id=p_invitee_id and b.blocked_id=v_uid)) then
  raise exception 'Bu kullanıcıya davet gönderilemiyor.';
 end if;
 if exists(select 1 from public.group_members gm where gm.group_id=p_group_id and gm.user_id=p_invitee_id and gm.status in ('approved','pending')) then
  raise exception 'Bu kişi zaten grubun üyesi veya katılım isteği var.';
 end if;
 select count(*) into v_count from public.group_members where group_id=p_group_id and member_role='member' and status in ('approved','pending');
 if v_count>=coalesce(v_group.required_members,15) then raise exception 'Grup tester kapasitesini doldurdu.'; end if;
 if char_length(coalesce(p_message,''))>500 then raise exception 'Davet mesajı en fazla 500 karakter olabilir.'; end if;
 update public.group_invitations set status='revoked',responded_at=now()
 where group_id=p_group_id and invitee_id=p_invitee_id and status='pending';
 insert into public.group_invitations(group_id,inviter_id,invitee_id,custom_message)
 values(p_group_id,v_uid,p_invitee_id,trim(coalesce(p_message,''))) returning * into v_invitation;
 select display_name into v_name from public.profiles where user_id=v_uid;
 return jsonb_build_object('id',v_invitation.id,'invite_token',v_invitation.invite_token,
 'group_id',v_group.id,'group_name',v_group.name,'group_description',v_group.description,
 'inviter_name',coalesce(v_name,'Betalink kullanıcısı'));
end; $$;

create or replace function private.betalink_respond_group_invitation(p_invitation_id uuid,p_accept boolean)
returns jsonb language plpgsql security definer
set search_path=public,auth,private,pg_temp as $$
declare v_uid uuid:=auth.uid(); v_inv public.group_invitations%rowtype;
v_group public.tester_groups%rowtype; v_count integer;
begin
 if v_uid is null then raise exception 'Oturum gerekli.'; end if;
 select * into v_inv from public.group_invitations where id=p_invitation_id for update;
 if not found or v_inv.invitee_id<>v_uid then raise exception 'Davet bulunamadı.'; end if;
 if v_inv.status<>'pending' then raise exception 'Bu davet artık geçerli değil.'; end if;
 if p_accept then
  select * into v_group from public.tester_groups where id=v_inv.group_id for update;
  if not found then raise exception 'Grup artık mevcut değil.'; end if;
  select count(*) into v_count from public.group_members where group_id=v_inv.group_id and member_role='member' and status='approved';
  if v_count>=coalesce(v_group.required_members,15) then raise exception 'Grup kapasitesi dolmuş.'; end if;
  if exists(select 1 from public.blocked_users b where
   (b.blocker_id=v_uid and b.blocked_id=v_inv.inviter_id) or (b.blocker_id=v_inv.inviter_id and b.blocked_id=v_uid)) then
   raise exception 'Bu davete katılım şu anda mümkün değil.';
  end if;
  insert into public.group_members(group_id,user_id,member_role,status)
  values(v_inv.group_id,v_uid,'member','approved')
  on conflict(group_id,user_id) do update set status='approved';
  update public.group_invitations set status='accepted',responded_at=now() where id=p_invitation_id;
 else
  update public.group_invitations set status='declined',responded_at=now() where id=p_invitation_id;
 end if;
 return jsonb_build_object('success',true,'status',case when p_accept then 'accepted' else 'declined' end);
end; $$;

create or replace function public.betalink_send_group_invitation(p_group_id uuid,p_invitee_id uuid,p_message text default '')
returns jsonb language sql security invoker set search_path=public,private,pg_temp
as $$ select private.betalink_send_group_invitation(p_group_id,p_invitee_id,p_message); $$;
create or replace function public.betalink_respond_group_invitation(p_invitation_id uuid,p_accept boolean)
returns jsonb language sql security invoker set search_path=public,private,pg_temp
as $$ select private.betalink_respond_group_invitation(p_invitation_id,p_accept); $$;
revoke all on function private.betalink_send_group_invitation(uuid,uuid,text) from public,anon;
revoke all on function private.betalink_respond_group_invitation(uuid,boolean) from public,anon;
grant execute on function private.betalink_send_group_invitation(uuid,uuid,text) to authenticated;
grant execute on function private.betalink_respond_group_invitation(uuid,boolean) to authenticated;
revoke all on function public.betalink_send_group_invitation(uuid,uuid,text) from public,anon;
revoke all on function public.betalink_respond_group_invitation(uuid,boolean) from public,anon;
grant execute on function public.betalink_send_group_invitation(uuid,uuid,text) to authenticated;
grant execute on function public.betalink_respond_group_invitation(uuid,boolean) to authenticated;

create or replace function public.notify_betalink_group_invitation()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare v_group_name text;
begin
 select name into v_group_name from public.tester_groups where id=new.group_id;
 if tg_op='INSERT' then
  insert into public.notifications(recipient_id,notification_type,title,body)
  values(new.invitee_id,'group_invitation','Yeni grup davetin var',
  coalesce(v_group_name,'Bir grup') || ' grubuna davet edildin. Daveti Kişiler bölümünden yanıtlayabilirsin.');
 elsif old.status is distinct from new.status and new.status in ('accepted','declined') then
  insert into public.notifications(recipient_id,notification_type,title,body)
  values(new.inviter_id,'group_invitation_response','Grup davetin yanıtlandı',
  coalesce(v_group_name,'Grup') || ' davetin ' || case when new.status='accepted' then 'kabul edildi.' else 'reddedildi.' end);
 end if;
 return new;
end; $$;
revoke all on function public.notify_betalink_group_invitation() from public,anon,authenticated;
drop trigger if exists on_betalink_group_invitation_notification on public.group_invitations;
create trigger on_betalink_group_invitation_notification after insert or update of status
on public.group_invitations for each row execute function public.notify_betalink_group_invitation();
