# Unterwelt 1931

Mobile PWA with a January 1931–December 1933 single-player campaign, offline saves, optional cloud saves and private online multiplayer.

## Deployment
- Render Static Site: `main` branch
- Build command: `node tests/rules.cjs`
- Publish directory: `dist`
- Live: https://unterwelt-1931.onrender.com

## Online architecture
The PWA keeps local saves for offline play. Optional cloud saves and 2–4 player private rooms use the existing Supabase project and its `game` Edge Function. The browser only contains the Supabase publishable key; privileged database access remains inside the Edge Function. Accounts use a private recovery code whose SHA-256 digest is stored server-side.

Cloud writes use optimistic version checks to prevent silent overwrites from a second device. Multiplayer rules, turn validation and random outcomes are authoritative on the server.

## Tests
Run `node tests/rules.cjs`. The suite covers campaign win/loss paths, economy, AP validation, territory conquest, police/passport logic, blackjack, tactical combat roles, map/PWA wiring and a complete authoritative multiplayer match.
