create index if not exists campaign_messages_sender_created_idx
  on public.campaign_messages(sender_id, created_at desc);
