# Project: Notion Task Digest (Java)

This is the always-loaded agent context - kept short and stable on purpose. See `README.md` for what this project is, `ARCHITECTURE.md` for design decisions/schema/structure, `TODO.md` for current status, roadmap, and known issues.

### Behavior

- Never make live calls to Notion, Discord, or any external API using real credentials from .env for your own testing or debugging. Use the captured sample responses (src/test/resources/notion-sample-long-term.json, notion-sample-tasks.json, notion-sample-minutes.json) and mocked clients instead. If a real end-to-end call is ever needed, ask first.
- Never guess Notion's JSON response shape — match the corresponding src/test/resources/notion-sample-*.json fixture exactly for each database.
- Never write a Claude API key or HTTP client for the LLM step — the only path is the claude -p subprocess via ClaudeCodeClient.
- Don't add retry/backoff, caching, or config layers unless asked — this is a single-cron-run script, not a service.
- Branch convention: `dev` is the active work branch; `main` gets fast-forwarded to `dev` periodically and is also where the homelab deploy (real cron job, crontab entries, deploy-specific config) always lives - don't be surprised by deploy-only commits appearing on `main` directly rather than `dev`.

### Secrets

An agent should never direclty read or print these contents. They are .gitignored and should only ever be read by the program.

.env (single file, gitignored) holds:
- NOTION_TOKEN=...
- NOTION_LONG_TERM_DB_ID=...
- NOTION_TASKS_DB_ID=...
- NOTION_MINUTES_DB_ID=...
- DISCORD_WEBHOOK_URL=...
- CLAUDE_EXECUTABLE_PATH=... — not a secret, but machine-specific config kept in the same single file for simplicity. Must be an absolute path (e.g. `which claude` on that machine) - the dev machine and the homelab machine that runs the cron job have `claude` installed in different places, and a bare `claude` isn't reliably found under cron's minimal PATH (see ARCHITECTURE.md decision #1).
