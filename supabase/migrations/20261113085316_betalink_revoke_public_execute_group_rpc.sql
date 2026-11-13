REVOKE EXECUTE ON FUNCTION private.betalink_group_snapshot(uuid) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION private.betalink_set_group_email_consent(uuid, boolean, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION private.betalink_group_snapshot(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION private.betalink_set_group_email_consent(uuid, boolean, text) TO authenticated;
