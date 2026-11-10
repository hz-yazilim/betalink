-- Permit a new invite after an earlier invitation was declined or revoked.
alter table public.group_invitations drop constraint if exists group_invitations_unique_pending;
create unique index if not exists group_invitations_one_pending_per_user_group
  on public.group_invitations(group_id,invitee_id) where status='pending';
create or replace function private.betalink_send_group_invitation(
 p_group_id uuid,p_invitee_id uuid,p_message text default ''
) returns jsonb language plpgsql security definer
set search_path=public,auth,private,pg_temp as $$
declare v_uid uuid:=auth.uid(); v_group public.tester_groups%rowtype; v_name text;
v_invitation public.group_invitations%rowtype; v_count integer;
begin
 if v_uid is null then raise exception 'Oturum gerekli.'; end if;
 if p_invitee_id is null or p_invitee_id=v_uid then raise exception 'Geçerli bir kişi seç.'; end if;
 select * into v_group from public.tester_groups where id=p_group_id for update;
 if not found then raise exception 'Grup bulunamadı.'; end if;
 if v_group.created_by<>v_uid then raise exception 'Yalnızca kendi grubuna davet gönderebilirsin.'; end if;
 if not exists(select 1 from public.profiles p where p.user_id=p_invitee_id and p.allow_group_invites=true) then raise exception 'Bu kullanıcı grup davetlerini kapatmış veya bulunamadı.'; end if;
 if exists(select 1 from public.blocked_users b where
  (b.blocker_id=v_uid and b.blocked_id=p_invitee_id) or (b.blocker_id=p_invitee_id and b.blocked_id=v_uid)) then raise exception 'Bu kullanıcıya davet gönderilemiyor.'; end if;
 if exists(select 1 from public.group_members gm where gm.group_id=p_group_id and gm.user_id=p_invitee_id and gm.status in ('approved','pending')) then raise exception 'Bu kişi zaten grubun üyesi veya katılım isteği var.'; end if;
 select count(*) into v_count from public.group_members where group_id=p_group_id and member_role='member' and status in ('approved','pending');
 if v_count>=coalesce(v_group.required_members,15) then raise exception 'Grup tester kapasitesini doldurdu.'; end if;
 if char_length(coalesce(p_message,''))>500 then raise exception 'Davet mesajı en fazla 500 karakter olabilir.'; end if;
 update public.group_invitations set status='revoked',responded_at=now() where group_id=p_group_id and invitee_id=p_invitee_id and status='pending';
 insert into public.group_invitations(group_id,inviter_id,invitee_id,custom_message)
 values(p_group_id,v_uid,p_invitee_id,trim(coalesce(p_message,''))) returning * into v_invitation;
 select display_name into v_name from public.profiles where user_id=v_uid;
 return jsonb_build_object('id',v_invitation.id,'invite_token',v_invitation.invite_token,
 'group_id',v_group.id,'group_name',v_group.name,'group_description',v_group.description,
 'inviter_name',coalesce(v_name,'Betalink kullanıcısı'));
end; $$;
