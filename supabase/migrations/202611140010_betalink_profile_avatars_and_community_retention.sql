-- Profile image uploads and two-hour community feed retention.
-- Profile images are public by design (they are shown in the public tester directory),
-- while writes and deletes are restricted to the authenticated user's own folder.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('profile-avatars', 'profile-avatars', true, 5242880, array['image/jpeg','image/png','image/webp'])
on conflict (id) do update set
  public = true,
  file_size_limit = 5242880,
  allowed_mime_types = array['image/jpeg','image/png','image/webp'];

drop policy if exists profile_avatar_upload_own_folder on storage.objects;
create policy profile_avatar_upload_own_folder
on storage.objects for insert to authenticated
with check (
  bucket_id = 'profile-avatars'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);

drop policy if exists profile_avatar_delete_own_folder on storage.objects;
create policy profile_avatar_delete_own_folder
on storage.objects for delete to authenticated
using (
  bucket_id = 'profile-avatars'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);

create index if not exists community_posts_created_at_cleanup_idx
  on public.community_posts (created_at);

do $$
declare existing_job_id bigint;
begin
  select jobid into existing_job_id
  from cron.job
  where jobname = 'betalink-delete-expired-community-posts'
  limit 1;

  if existing_job_id is not null then
    perform cron.unschedule(existing_job_id);
  end if;

  perform cron.schedule(
    'betalink-delete-expired-community-posts',
    '* * * * *',
    $job$
      delete from public.community_posts
      where created_at < now() - interval '2 hours';
    $job$
  );
end
$$;

delete from public.community_posts
where created_at < now() - interval '2 hours';
