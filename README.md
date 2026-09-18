# Unterwelt 1931

Mobile PWA with offline single-player, optional cloud saves and private online multiplayer.

## Deployment
- Render Static Site: `main` branch
- Build command: `node tests/rules.cjs`
- Publish directory: `dist`
- Live: https://unterwelt-1931.onrender.com

## Online architecture
The PWA keeps local saves for offline play. Optional cloud saves and 2–4 player private rooms use the existing Supabase project and its `game` Edge Function. The browser only contains the Supabase publishable key; privileged database access remains inside the Edge Function. Accounts use a private recovery code whose SHA-256 digest is stored server-side.

Cloud writes use optimistic version checks to prevent silent overwrites from a second device. Multiplayer rules, turn validation and random outcomes are authoritative on the server.

## Tests
Run `node tests/rules.cjs`. These checks use a mocked DOM and also parse the online client for syntax/PWA wiring.
