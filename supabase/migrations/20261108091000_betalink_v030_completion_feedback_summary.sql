-- Automatically notify campaign owners with a feedback summary when a campaign completes.
create or replace function public.betalink_notify_campaign_completion_summary()
returns trigger
language plpgsql
security definer
set search_path=public
as $fn$
declare
  v_feedback_count integer;
  v_avg numeric;
  v_bugs integer;
  v_suggestions integer;
  v_experiences integer;
begin
  if new.status='completed' and old.status is distinct from 'completed' then
    select count(*),round(avg(rating)::numeric,2),
      count(*) filter(where feedback_type='bug'),
      count(*) filter(where feedback_type='suggestion'),
      count(*) filter(where feedback_type='experience')
    into v_feedback_count,v_avg,v_bugs,v_suggestions,v_experiences
    from public.feedback where campaign_id=new.id;
    insert into public.notifications(recipient_id,notification_type,title,body)
    values(new.developer_id,'campaign_summary','Test tamamlandı · Geri bildirim özeti',
      format('“%s” kampanyası tamamlandı. %s geri bildirim, ortalama puan: %s/5, %s hata, %s öneri, %s deneyim bildirimi.',
        new.app_name,v_feedback_count,coalesce(v_avg::text,'henüz yok'),v_bugs,v_suggestions,v_experiences));
  end if;
  return new;
end
$fn$;

drop trigger if exists betalink_campaign_completion_summary on public.campaigns;
create trigger betalink_campaign_completion_summary
after update of status on public.campaigns
for each row execute function public.betalink_notify_campaign_completion_summary();