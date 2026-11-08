-- Fire the completion summary when the 14-day tracking clock completes.
drop trigger if exists betalink_campaign_completion_summary on public.campaigns;
create or replace function public.betalink_notify_tracking_completion_summary()
returns trigger language plpgsql security definer set search_path=public as $fn$
declare c record; v_feedback_count integer; v_avg numeric; v_bugs integer; v_suggestions integer; v_experiences integer;
begin
 if new.tracking_status='completed' and old.tracking_status is distinct from 'completed' then
  select id,developer_id,app_name into c from public.campaigns where id=new.campaign_id;
  if c.id is not null then
   select count(*),round(avg(rating)::numeric,2),count(*) filter(where feedback_type='bug'),
    count(*) filter(where feedback_type='suggestion'),count(*) filter(where feedback_type='experience')
   into v_feedback_count,v_avg,v_bugs,v_suggestions,v_experiences
   from public.feedback where campaign_id=new.campaign_id;
   insert into public.notifications(recipient_id,notification_type,title,body)
   values(c.developer_id,'campaign_summary','14 günlük test tamamlandı · Geri bildirim özeti',
    format('“%s” testi tamamlandı. %s geri bildirim, ortalama puan: %s/5, %s hata, %s öneri, %s deneyim bildirimi.',
     c.app_name,v_feedback_count,coalesce(v_avg::text,'henüz yok'),v_bugs,v_suggestions,v_experiences));
  end if;
 end if;
 return new;
end $fn$;
drop trigger if exists betalink_tracking_completion_summary on public.campaign_tracking;
create trigger betalink_tracking_completion_summary after update of tracking_status on public.campaign_tracking
for each row execute function public.betalink_notify_tracking_completion_summary();