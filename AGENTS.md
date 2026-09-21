# Project: Notion Task Digest (Java)

### Goal: 
A cron-triggered, headless service that reads task/goal data from Notion, computes deterministic progress signals, and produces a short daily digest (sent to Discord) — including a ranked "what to work on today" suggestion. Built as deliberate practice for the agentic/interactive Claude Code harness, and as a Java-skill-building project (job-market motivated, not because Java is objectively the best tool for this task).

### Status
Update status here after functions are added to capture current progress in the repo.

Currently not began. Direct next steps:
- [x] Scaffold project, pom.xml
- [ ] Set up JUnit test skeleton against the sample

### Behavior

- Never make live calls to Notion, Discord, or any external API using real credentials from .env for your own testing or debugging. Use the captured sample responses (src/test/resources/notion-sample-long-term.json, notion-sample-tasks.json, notion-sample-minutes.json) and mocked clients instead. If a real end-to-end call is ever needed, ask first.
- Never guess Notion's JSON response shape — match the corresponding src/test/resources/notion-sample-*.json fixture exactly for each database.
- Never write a Claude API key or HTTP client for the LLM step — the only path is the claude -p subprocess via ClaudeCodeClient.
- Don't add retry/backoff, caching, or config layers unless asked — this is a single-cron-run script, not a service.

### Key architectural decisions already made
1. No API key. Rejected direct Anthropic API calls (footgun: standing secret on an unattended homelab machine). Instead, the Java program shells out to headless Claude Code (claude -p <prompt> --output-format text via ProcessBuilder) for the one LLM-reasoning step, authenticating via the existing Claude Code login already cached on that machine. Requires one manual interactive login beforehand. Confirmed (ClaudeCodeClient + manual verification harness): a stripped cron-like PATH and no TTY are both fine as long as (a) the claude binary is invoked by absolute path, since it typically lives under a user-local dir like ~/.local/bin that cron's default PATH doesn't include, and (b) HOME is preserved, since cached auth lives at ~/.claude/.credentials.json. Also confirmed: redirect the subprocess's stdin from /dev/null — otherwise claude -p spends ~3s waiting to see if piped input is coming and prints a warning into the same output stream you need to parse. The absolute path itself is never hardcoded in the Java code — the dev machine and the homelab cron machine have `claude` installed in different places, so it's read from .env's CLAUDE_EXECUTABLE_PATH (one differing value per machine, no code change needed). Revalidate the manual verification harness (not on a schedule, only when triggered): first deploy to the homelab machine, any `claude` CLI upgrade, any re-login/re-auth, or as a first diagnostic if a cron run fails silently at the LLM step.
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

### Expected project structure

```bash
morning-brief/
├── pom.xml
├── .env                          # NOTION_TOKEN, DB IDs, DISCORD_WEBHOOK_URL — no Claude API key needed
├── CLAUDE.md
├── src/main/java/digest/
│   ├── notion/NotionExtractor.java, NotionModels.java
│   ├── aggregate/SnapshotBuilder.java
│   ├── llm/PromptBuilder.java, ClaudeCodeClient.java   # subprocess wrapper, not HTTP client
│   ├── delivery/DiscordNotifier.java
│   ├── log/DigestLog.java
│   └── Main.java
└── src/test/java/digest/         # JUnit, mocked against a real captured Notion API sample response
```

### Validation

```bash
mvn compile # Recompile project
mvn test # Run JUnit tests
```

### Secrets

An agent should never direclty read or print these contents. They are .gitignored and should only ever be read by the program.

.env (single file, gitignored) holds:
- NOTION_TOKEN=...
- NOTION_LONG_TERM_DB_ID=...
- NOTION_TASKS_DB_ID=...
- NOTION_MINUTES_DB_ID=...
- DISCORD_WEBHOOK_URL=...
- CLAUDE_EXECUTABLE_PATH=... — not a secret, but machine-specific config kept in the same single file for simplicity. Must be an absolute path (e.g. `which claude` on that machine) - the dev machine and the homelab machine that runs the cron job have `claude` installed in different places, and a bare `claude` isn't reliably found under cron's minimal PATH (see architectural decision #1).
