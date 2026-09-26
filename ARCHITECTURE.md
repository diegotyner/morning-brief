# Architecture

See `README.md` for what this project is and why. This file holds the design decisions, data model, and project structure - the parts that change rarely, once a decision is actually made. For current status and the open task list, see `TODO.md`. For the always-loaded agent behavior rules, see `AGENTS.md`.

### Key architectural decisions already made
1. No API key. Rejected direct Anthropic API calls (footgun: standing secret on an unattended homelab machine). Instead, the Java program shells out to headless Claude Code (claude -p <prompt> --output-format text via ProcessBuilder) for the one LLM-reasoning step, authenticating via the existing Claude Code login already cached on that machine. Requires one manual interactive login beforehand. Confirmed (ClaudeCodeClient + manual verification harness): a stripped cron-like PATH and no TTY are both fine as long as (a) the claude binary is invoked by absolute path, since it typically lives under a user-local dir like ~/.local/bin that cron's default PATH doesn't include, and (b) HOME is preserved, since cached auth lives at ~/.claude/.credentials.json. Also confirmed: redirect the subprocess's stdin from /dev/null — otherwise claude -p spends ~3s waiting to see if piped input is coming and prints a warning into the same output stream you need to parse. The absolute path itself is never hardcoded in the Java code — the dev machine and the homelab cron machine have `claude` installed in different places, so it's read from .env's CLAUDE_EXECUTABLE_PATH (one differing value per machine, no code change needed). Revalidate the manual verification harness (not on a schedule, only when triggered): first deploy to the homelab machine, any `claude` CLI upgrade, any re-login/re-auth, or as a first diagnostic if a cron run fails silently at the LLM step. On that last point: a cron run no longer needs to fail silently at all - `ClaudeCodeClient.isLoggedIn()` checks `claude auth status --json` up front and Main alerts via Discord instead of just failing, so a stale/expired login now surfaces as a clear notification rather than a missing digest with no explanation.
2. Notion owns simple aggregates; the script owns anything relational/ordered. Total minutes, session count, % complete = native Notion rollups/formulas. "Latest session note per task" = resolved by the extraction script (a sorted, filtered Notion API query), not a fragile multi-field Notion formula chain (tried and abandoned).
3. Stateless LLM call + bounded one-day lookback, not accumulating conversational memory. Yesterday's ranking gets stored in a small JSON log and re-fed into today's prompt for continuity ("you were told to do X — did that happen?"). Full running history deliberately avoided (cost, reproducibility, debuggability).
4. Maven, not a bare javac/java setup. This project has real dependencies (Jackson, JUnit) and multiple files, and Maven/Gradle familiarity is itself part of the Java job-market skill being built.

### Current Notion schema
*Long Term* — Name | Description | Status | Total Minutes | Target Date | Child Tasks (relation → Tasks)
- Description should convey full project context + what "done" looks like.

*Tasks* — Name | Created | Parent (relation → Long Term) | Status | Priority | Next Action | Estimated Minutes | Minutes Frac (%) | Blockers | Log Minutes (relation → Minutes) | Total Mins (rollup) | Sessions (rollup) | Edited
- Next Action = highest-value manual field, one sentence, updated every session.
- Possible addition not yet made: a Blocker text field, separate from Next Action.

*Minutes* — Name | Date | Minutes | Notes | Task (relation → Tasks) | Last edited time
- Notes = the other highest-value manual field.

### Project structure

```bash
morning-brief/
├── pom.xml
├── .env                          # NOTION_TOKEN, DB IDs, DISCORD_WEBHOOK_URL — no Claude API key needed
├── AGENTS.md                     # always-loaded agent behavior rules + secrets
├── ARCHITECTURE.md               # this file
├── TODO.md                       # current status, roadmap, known issues
├── src/main/java/digest/
│   ├── notion/NotionExtractor.java, NotionModels.java
│   ├── aggregate/SnapshotBuilder.java
│   ├── llm/PromptBuilder.java, ClaudeCodeClient.java   # subprocess wrapper, not HTTP client
│   ├── delivery/DiscordNotifier.java
│   ├── log/DigestLog.java
│   └── Main.java
└── src/test/java/digest/         # JUnit, mocked against real captured Notion API sample fixtures
```
