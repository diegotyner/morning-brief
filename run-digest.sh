#!/bin/bash
# Wrapper for the cron job. Exists so the exact invocation cron uses lives in version control
# and can be run by hand for testing (./run-digest.sh --dry-run), rather than only existing
# inside `crontab -e` output.
set -euo pipefail

# cron's own PATH is minimal and may not include mvn/java - set a normal one explicitly rather
# than relying on whatever PATH cron happens to hand this script (same lesson CLAUDE_EXECUTABLE_PATH
# already taught: never assume PATH under cron, use something explicit instead).
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$HOME/.local/bin"

# Self-locating: works no matter what directory this script is invoked from.
cd "$(dirname "$0")"

mvn -q compile exec:java -Dexec.args="$*"
