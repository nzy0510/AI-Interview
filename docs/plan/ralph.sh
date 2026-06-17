#!/bin/bash
# Ralph Wiggum - tiny loop around a JSON PRD backlog.
#
# Self-running Claude Code loop:
#   ./ralph.sh 20
#   ./ralph.sh --iterations 20

set -euo pipefail

MAX_ITERATIONS=""

usage() {
  echo "Usage: ./ralph.sh [max_iterations|-n max_iterations|--iterations max_iterations]"
}

set_max_iterations() {
  if [[ -n "$MAX_ITERATIONS" ]]; then
    echo "max_iterations was provided more than once."
    exit 1
  fi
  if [[ ! "$1" =~ ^[0-9]+$ ]]; then
    echo "Invalid max_iterations: $1"
    exit 1
  fi
  MAX_ITERATIONS="$1"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tool)
      if [[ "${2:-}" != "claude" ]]; then
        echo "Ralph is fixed to Claude Code. Do not pass another tool."
        exit 1
      fi
      shift 2
      ;;
    --tool=*)
      if [[ "${1#*=}" != "claude" ]]; then
        echo "Ralph is fixed to Claude Code. Do not pass another tool."
        exit 1
      fi
      shift
      ;;
    -n|--iterations|--max-iterations)
      set_max_iterations "${2:-}"
      shift 2
      ;;
    --iterations=*|--max-iterations=*)
      set_max_iterations "${1#*=}"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      if [[ "$1" =~ ^[0-9]+$ ]]; then
        set_max_iterations "$1"
      else
        echo "Unknown argument: $1"
        exit 1
      fi
      shift
      ;;
  esac
done

if [[ -z "$MAX_ITERATIONS" ]]; then
  echo "Missing required max_iterations."
  usage
  exit 1
fi

if [[ "$MAX_ITERATIONS" -lt 1 ]]; then
  echo "max_iterations must be greater than 0."
  exit 1
fi

if ! command -v claude >/dev/null 2>&1; then
  echo "Missing required Claude Code CLI: claude"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
PRD_FILE="$SCRIPT_DIR/prd.json"
PROGRESS_FILE="$SCRIPT_DIR/progress.txt"

if [[ ! -f "$PRD_FILE" ]]; then
  echo "Missing PRD backlog: $PRD_FILE"
  exit 1
fi

touch "$PROGRESS_FILE"

RALPH_PROMPT="You are Ralph, an autonomous coding agent working in $REPO_DIR.

Use these files as your working backlog and progress log:
@$PRD_FILE
@$PROGRESS_FILE

Rules:
1. This script is self-contained. Do not depend on any CLAUDE.md file for task instructions.
2. Choose exactly one incomplete task for this iteration.
3. Use the PRD JSON as backlog context, not as a rigid phase plan or file-order checklist.
4. Choose the highest-priority incomplete task that is small enough to finish in this iteration, using your judgment if multiple tasks are available.
5. Do not start a second task after finishing the selected task.
6. Implement the selected task and verify it. Do not mark a task complete without verification.
7. Update the PRD JSON task status/evidence before ending the iteration.
8. Append a note to $PROGRESS_FILE. The progress note must include the development process, bugs encountered, and how each bug was fixed. If no bug was encountered, say so explicitly.
9. Use the progress note to leave a clear note for the next person working in the codebase.
10. Make a git commit for the completed feature, but only after the feature works and the relevant CI/local checks pass.
11. If verification or CI/local checks fail, fix the issue before committing; if you cannot fix it, do not commit and record the failure in $PROGRESS_FILE.
12. If you are blocked by a required user decision, missing dependency, unavailable environment, merge conflict, or another issue you cannot resolve safely, append the blocker, evidence, and requested user action to $PROGRESS_FILE, then end your final response with:
<promise>BLOCKED</promise>
13. If every PRD task is complete and verified, end your final response with:
<promise>COMPLETE</promise>"

cd "$REPO_DIR"

echo "Starting Ralph: tool=claude mode=self-loop iterations=$MAX_ITERATIONS"

for i in $(seq 1 "$MAX_ITERATIONS"); do
  echo ""
  echo "=== Ralph iteration $i/$MAX_ITERATIONS ==="

  OUTPUT="$(claude --dangerously-skip-permissions --print "$RALPH_PROMPT" 2>&1)" || true
  printf '%s\n' "$OUTPUT"

  if echo "$OUTPUT" | grep -q "<promise>COMPLETE</promise>"; then
    echo "Ralph complete."
    exit 0
  fi

  if echo "$OUTPUT" | grep -q "<promise>BLOCKED</promise>"; then
    echo "Ralph blocked. Check $PROGRESS_FILE."
    exit 2
  fi
done

echo "Ralph stopped after $MAX_ITERATIONS iterations."
echo "Check $PRD_FILE and $PROGRESS_FILE."
exit 1
