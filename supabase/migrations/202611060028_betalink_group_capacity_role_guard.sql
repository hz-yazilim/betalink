create or replace function public.guard_betalink_group_capacity()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare current_count integer;
begin
 if new.status='approved' and new.member_role='member'
   and (tg_op='INSERT' or old.status is distinct from 'approved' or old.member_role is distinct from 'member') then
  perform 1 from public.tester_groups where id=new.group_id for update;
  select count(*) into current_count from public.group_members
   where group_id=new.group_id and status='approved' and member_role='member'
    and (tg_op='INSERT' or id<>new.id);
  if current_count>=15 then raise exception 'Bu grup 15 tester kapasitesini doldurdu.'; end if;
 end if;
 return new;
end; $$;
drop trigger if exists betalink_group_capacity_guard on public.group_members;
create trigger betalink_group_capacity_guard before insert or update of status,member_role on public.group_members
for each row execute function public.guard_betalink_group_capacity();
drop trigger if exists betalink_group_clock_refresh on public.group_members;
create trigger betalink_group_clock_refresh after insert or delete or update of status,member_role on public.group_members
for each row execute function public.refresh_betalink_group_clock();