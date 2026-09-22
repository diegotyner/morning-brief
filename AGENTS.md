# Project: Notion Task Digest (Java)

### Goal: 
A cron-triggered, headless service that reads task/goal data from Notion, computes deterministic progress signals, and produces a short daily digest (sent to Discord) — including a ranked "what to work on today" suggestion. Built as deliberate practice for the agentic/interactive Claude Code harness, and as a Java-skill-building project (job-market motivated, not because Java is objectively the best tool for this task).

### Status
Update status here after functions are added to capture current progress in the repo.

Done:
- [x] Scaffold project, pom.xml
- [x] ClaudeCodeClient: real subprocess wrapper around headless `claude -p`, unit-tested and empirically verified under cron-like conditions (see architectural decision #1)
- [x] NotionModels + NotionExtractor: records + small reusable per-property-shape deserializers (title/rich_text, status/select, number/rollup/formula, relation, date, created_time/last_edited_time), tested against the three fixtures. `fetchX` empirically verified live (see below). Along the way, found and removed 3 undocumented, unused Tasks properties (Place, a bare Date, Total Mins Copy) from both the real capture and the sanitized fixture — since deleted from Notion itself, so the fixture now matches the live schema exactly again.
- [x] SnapshotBuilder: joins tasks to their parent project(s) via each task's own Parent relation (not the project's Child Tasks - not assumed authoritative), tolerates dangling relation ids (mirrors a real one found in the data), resolves "latest session note per task" by max-date over that task's Minutes entries. No filtering/ranking - every project/task passed in appears in the output; that judgment is Claude's job at PromptBuilder, not this layer's.
- [x] DigestLog: minimal on purpose - one entry, not an accumulating history (per architectural decision #3). `LogEntry(String date, String recommendation)` stores the LLM's raw text as-is; `date` is a plain ISO string, not `LocalDate`, to avoid a jackson-datatype-jsr310 dependency for one flat field. No atomic write, no directory auto-creation, no retry - deliberately skipped for now to prioritize reaching a working demo over hardening.

Path to the working demo (complete):
- [x] PromptBuilder: plain-text prompt (framing + yesterday's recommendation if any + per-project/per-task rendering + closing ask), null-safe so unset fields are omitted rather than printed as "null". First-draft wording - to be tuned once real output from an actual run is visible, not polished blind.
- [x] DiscordNotifier: webhook POST via java.net.http.HttpClient, JSON body built with Jackson (not string concat), truncates to Discord's 2000-char content limit. Empirically verified live (see below).
- [x] Main.java: wired NotionExtractor -> SnapshotBuilder -> PromptBuilder/ClaudeCodeClient -> DiscordNotifier -> DigestLog. Has a `--dry-run` flag (parses the checked-in sanitized fixtures instead of NotionExtractor.fetchX, prints to stdout instead of DiscordNotifier.send) used to verify the pipeline safely before the live run - see roadmap below for how `--dry-run` was validated.
- [x] First real end-to-end run against live Notion + live Discord (you asked for it explicitly). No errors - real Notion fetch, real SnapshotBuilder join, real Claude ranking (correctly identified this very project as the highest-priority task, since other work is blocked on it), real Discord post, real digest-log.json write. This is the first genuinely working version of the whole tool, not just individually-tested pieces.

MVP pipeline complete - everything below is hardening/polish, not required for the tool to work:
- [ ] Guard NotionExtractor.fetchAllPages against an infinite loop if Notion ever returns has_more=true with no next_cursor - low probability, but the failure mode is a cron job hanging forever, not a clean failure
- [ ] Revisit whether TaskSnapshot needs created/edited timestamps once PromptBuilder's actual "how stale is this task" needs are known - not needed yet, cheap to add later

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
