# Supabase backend

The deployed Edge Function is named `game`.

- Client authentication uses a private `uw1_...` recovery key.
- Only the SHA-256 digest of that key is stored in `uw_accounts`.
- Cloud saves use optimistic version checks.
- Multiplayer room state is validated and mutated inside the Edge Function.
- Database tables are not exposed directly to browser roles.
- Never commit a service-role or secret key. Supabase injects `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` at runtime.

Live project ref: `fnknttplbqwkzarbkbzb`.
