# morning-brief

A cron-triggered service that turns your Notion tasks and projects into a short daily digest — including a ranked "what to work on today" suggestion — delivered straight to Discord.

## Features

- Pulls live data from Notion: long-term projects, tasks, and logged work sessions
- Computes real progress signals — total time spent, session counts, latest notes per task
- Uses Claude to rank what's most worth doing today, with context on what you were told to do yesterday and whether it happened
- Delivers a daily digest to Discord automatically
- Runs unattended on a cron schedule — no manual trigger needed

#### Why Claude Code instead of an API key

The ranking step shells out to headless Claude Code (`claude -p ... --output-format text`) instead of calling the Anthropic API directly, so there's no need to pay API key fees — it authenticates using the machine's already-cached Claude Code login instead.

## Data model (Notion)

- **Long Term** — Name | Description | Status | Total Minutes | Target Date | Child Tasks (relation → Tasks)
- **Tasks** — Name | Created | Parent (relation → Long Term) | Status | Priority | Next Action | Estimated Minutes | Minutes Frac (%) | Blockers | Log Minutes (relation → Minutes) | Total Mins (rollup) | Sessions (rollup) | Edited
- **Minutes** — Name | Date | Minutes | Notes | Task (relation → Tasks) | Last edited time

`Next Action` and `Notes` are the two highest-value manual fields, updated every work session.

## Project structure

```bash
morning-brief/
├── pom.xml
├── .env                          # NOTION_TOKEN, DB IDs, DISCORD_WEBHOOK_URL, CLAUDE_EXECUTABLE_PATH
├── src/main/java/digest/
│   ├── notion/NotionExtractor.java, NotionModels.java
│   ├── aggregate/SnapshotBuilder.java
│   ├── llm/PromptBuilder.java, ClaudeCodeClient.java   # subprocess wrapper, not an HTTP client
│   ├── delivery/DiscordNotifier.java
│   ├── log/DigestLog.java
│   └── Main.java
└── src/test/java/digest/         # JUnit, mocked against captured Notion API sample fixtures
```

## Building & testing

```bash
mvn compile   # recompile
mvn test      # run the JUnit suite
```

## Status

Working MVP - runs end-to-end against live Notion and Discord. See `TODO.md` for the current task checklist and known issues, `ARCHITECTURE.md` for design decisions, and `AGENTS.md` for agent-specific behavior rules.
