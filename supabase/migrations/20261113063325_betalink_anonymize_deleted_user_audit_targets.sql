-- When an account is deleted, retain the audit event but remove the deleted user's
-- identifier from historical user-target records.
CREATE OR REPLACE FUNCTION private.betalink_anonymize_deleted_user_audit_targets()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $fn$
BEGIN
  UPDATE public.admin_audit_logs
  SET target_id = NULL,
      metadata = coalesce(metadata, '{}'::jsonb)
        - 'email' - 'account_email' - 'contact_email' - 'display_name' - 'phone'
  WHERE target_type = 'user'
    AND target_id = OLD.user_id::text;
  RETURN OLD;
END;
$fn$;

DROP TRIGGER IF EXISTS anonymize_deleted_user_audit_targets ON public.profiles;
CREATE TRIGGER anonymize_deleted_user_audit_targets
AFTER DELETE ON public.profiles
FOR EACH ROW
EXECUTE FUNCTION private.betalink_anonymize_deleted_user_audit_targets();

REVOKE ALL ON FUNCTION private.betalink_anonymize_deleted_user_audit_targets() FROM PUBLIC, anon, authenticated;
