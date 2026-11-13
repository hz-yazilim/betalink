-- Fix PostgreSQL varchar -> text return-type mismatch in consented account-email RPCs.
-- auth.users.email is varchar; the RPC contracts intentionally expose text.
-- Access checks and opt-in filters remain unchanged.

CREATE OR REPLACE FUNCTION private.betalink_group_member_account_emails(p_group_id uuid)
RETURNS TABLE(user_id uuid, display_name text, account_email text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, private, pg_temp
AS $function$
DECLARE
  uid uuid := auth.uid();
  owner_id uuid;
BEGIN
  IF uid IS NULL THEN
    RAISE EXCEPTION 'Oturum gerekli.';
  END IF;

  SELECT tg.created_by INTO owner_id
  FROM public.tester_groups tg
  WHERE tg.id = p_group_id;

  IF owner_id IS DISTINCT FROM uid THEN
    RAISE EXCEPTION 'Yalnızca grup kurucusu üye hesap e-postalarını görebilir.';
  END IF;

  RETURN QUERY
    SELECT
      gm.user_id,
      COALESCE(p.display_name, 'Betalink kullanıcısı')::text,
      COALESCE(u.email, '')::text
    FROM public.group_members gm
    JOIN auth.users u ON u.id = gm.user_id
    LEFT JOIN public.profiles p ON p.user_id = gm.user_id
    WHERE gm.group_id = p_group_id
      AND gm.status IN ('approved', 'pending')
      AND gm.share_account_email = true
    ORDER BY gm.joined_at ASC;
END;
$function$;

CREATE OR REPLACE FUNCTION private.betalink_campaign_member_account_emails(p_campaign_id uuid)
RETURNS TABLE(tester_id uuid, display_name text, account_email text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, private, pg_temp
AS $function$
DECLARE
  uid uuid := auth.uid();
  owner_id uuid;
BEGIN
  IF uid IS NULL THEN
    RAISE EXCEPTION 'Oturum gerekli.';
  END IF;

  SELECT c.developer_id INTO owner_id
  FROM public.campaigns c
  WHERE c.id = p_campaign_id;

  IF owner_id IS DISTINCT FROM uid THEN
    RAISE EXCEPTION 'Yalnızca kampanya sahibi tester hesap e-postalarını görebilir.';
  END IF;

  RETURN QUERY
    SELECT
      cm.tester_id,
      COALESCE(p.display_name, 'Betalink kullanıcısı')::text,
      COALESCE(u.email, '')::text
    FROM public.campaign_members cm
    JOIN auth.users u ON u.id = cm.tester_id
    LEFT JOIN public.profiles p ON p.user_id = cm.tester_id
    WHERE cm.campaign_id = p_campaign_id
      AND cm.share_account_email = true
    ORDER BY cm.joined_at ASC;
END;
$function$;

REVOKE ALL ON FUNCTION private.betalink_group_member_account_emails(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION private.betalink_campaign_member_account_emails(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION private.betalink_group_member_account_emails(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION private.betalink_campaign_member_account_emails(uuid) TO authenticated;
