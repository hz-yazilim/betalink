# Betalink password policy

## Application-side policy

The shared `PasswordPolicy` is used by registration and password recovery updates:

- At least 12 characters
- At least one ASCII lowercase letter
- At least one ASCII uppercase letter
- At least one digit
- At least one symbol accepted by Supabase Auth

Sign-in deliberately does not reject an existing user's password locally. This avoids changing the sign-in flow while the server policy is rolled out.

## Required hosted Supabase Auth settings

For the hosted Betalink project, open **Authentication → Sign In / Providers → Email → Password requirements** and set:

- Minimum password length: **12**
- Required characters: **lowercase + uppercase + digits + symbols**

Save the settings. These are hosted Auth service settings, not SQL migrations; changing a database migration or adding a local `supabase/config.toml` does not update the hosted Auth service.

The organization is currently on the Free plan. Password length and required-character rules are available in Auth settings; leaked-password (HaveIBeenPwned) protection is a separate Pro-plan feature and must remain off until the plan is upgraded.

## Compatibility note

Supabase's password symbol allow-list is:
`!@#$%^&*()_+-=[]{};':"\\|<>?,./`~

Existing accounts are not force-reset by this application change. If Supabase rejects a weak legacy password during sign-in after server-side enforcement is enabled, the user will need to use the password-recovery flow to set a compliant password.
