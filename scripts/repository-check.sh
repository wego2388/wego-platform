#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repository_root"

required_files=(
  "AGENTS.md"
  "docs/ENGINEERING_CONSTITUTION.md"
  "docs/architecture/AI_GOVERNANCE.md"
  "docs/architecture/BOUNDARIES.md"
  "docs/architecture/DATA_MODELING_RULES.md"
  "docs/architecture/OFFLINE_SYNC.md"
  "docs/architecture/SECURITY_MODEL.md"
  "docs/architecture/WEGO_ARCHITECTURE.md"
  "docs/execution/ENVIRONMENT_ASSESSMENT.md"
  "docs/execution/WEGO_EXECUTION_BOARD.md"
  "docs/execution/WEGO_000_VERIFICATION.md"
  "platform/contracts/openapi/v1/wego-api.yaml"
  "foundry/catalog/modules.json"
  "products/divers/product.manifest.json"
  "clients/sharm-divers-club/client.manifest.json"
  "clients/sharm-divers-club/release.lock.json"
  "infrastructure/compose/compose.yaml"
  ".github/workflows/ci.yml"
  ".github/dependabot.yml"
)

for required_file in "${required_files[@]}"; do
  if [[ ! -f "$required_file" ]]; then
    echo "Missing required foundation file: $required_file" >&2
    exit 1
  fi
done

# These checks scan the whole Mission table and every packet section by
# pattern, not by naming a specific WEGO-000/WEGO-001/... number — a check
# hardcoded to one mission's name silently stops protecting the invariant
# the moment a later mission exists (this happened once already: WEGO-001
# went COMPLETE with a hardcoded WEGO-000-only version of this script never
# noticing it existed).
active_packets="$(rg -c '^- \*\*Status:\*\* ACTIVE$' docs/execution/WEGO_EXECUTION_BOARD.md || true)"
active_packets="${active_packets:-0}"
adr_count="$(find docs/adr -maxdepth 1 -type f -name '[0-9][0-9][0-9][0-9]-*.md' | wc -l | tr -d ' ')"

if (( adr_count < 11 )); then
  echo "The platform requires at least eleven decision records; found $adr_count" >&2
  exit 1
fi

if (( active_packets > 1 )); then
  echo "At most one implementation packet may be ACTIVE across all missions; found $active_packets" >&2
  exit 1
fi

# Every "| WEGO-<n>[+] | <objective> | <status> |" row must use a
# recognized status, and at most one mission may be IN PROGRESS at a time —
# missions are worked sequentially, not in parallel. The row match itself
# only constrains the table shape (three pipe-delimited columns); the status
# text is validated separately so a malformed/unrecognized status value
# still surfaces the row instead of just failing to match and being
# silently skipped.
mission_rows="$(rg '^\| WEGO-[0-9]+\+? \| [^|]+ \| [^|]+ \|$' docs/execution/WEGO_EXECUTION_BOARD.md || true)"
if [[ -z "$mission_rows" ]]; then
  echo "No recognizable rows found in the Mission table" >&2
  exit 1
fi

in_progress_missions=0
in_progress_mission_token=""
while IFS= read -r row; do
  status="$(printf '%s' "$row" | sed -E 's/^\|[^|]*\|[^|]*\|[[:space:]]*(.*[^[:space:]])[[:space:]]*\|$/\1/')"
  case "$status" in
    COMPLETE) ;;
    "IN PROGRESS")
      in_progress_missions=$((in_progress_missions + 1))
      in_progress_mission_token="$(printf '%s' "$row" | grep -oE 'WEGO-[0-9]+' | head -1)"
      ;;
    NOT\ AUTHORIZED*) ;;
    *)
      echo "Unrecognized mission status '$status' in row: $row" >&2
      exit 1
      ;;
  esac
done <<< "$mission_rows"

if (( in_progress_missions > 1 )); then
  echo "At most one mission may be IN PROGRESS at a time; found $in_progress_missions" >&2
  exit 1
fi

# The two counts must agree: a mission IN PROGRESS with no ACTIVE packet
# means work is claimed but nothing is actually being tracked as underway;
# an ACTIVE packet with no mission IN PROGRESS means a packet is orphaned
# from the mission it's supposed to belong to. Checking each count's own
# upper bound independently (as above) doesn't catch either — this closes
# that gap.
if (( in_progress_missions == 1 && active_packets != 1 )); then
  echo "The IN PROGRESS mission must have exactly one ACTIVE packet; found $active_packets" >&2
  exit 1
fi
if (( in_progress_missions == 0 && active_packets != 0 )); then
  echo "An ACTIVE packet exists but no mission is IN PROGRESS; found $active_packets active packet(s)" >&2
  exit 1
fi

# The counts agreeing (above) isn't enough on its own: a WEGO-002 packet
# marked ACTIVE while WEGO-001 is the mission IN PROGRESS would still pass a
# pure count check (1 == 1) despite belonging to the wrong mission entirely.
# This walks the packet sections themselves (each "## WEGO-<n>[-<letter>]"
# heading down to its own "- **Status:**" line) and requires the ACTIVE
# one's mission number to match the IN-PROGRESS row's mission number.
if (( in_progress_missions == 1 )); then
  active_packet_token="$(awk '
    /^## WEGO-[0-9]+/ {
      match($0, /WEGO-[0-9]+/)
      current_token = substr($0, RSTART, RLENGTH)
    }
    /^- \*\*Status:\*\* ACTIVE$/ { print current_token }
  ' docs/execution/WEGO_EXECUTION_BOARD.md)"

  if [[ "$active_packet_token" != "$in_progress_mission_token" ]]; then
    echo "The ACTIVE packet section belongs to '${active_packet_token:-<none found>}' but the mission marked IN PROGRESS is '$in_progress_mission_token' — an ACTIVE packet must belong to the mission currently IN PROGRESS" >&2
    exit 1
  fi
fi

if find . \
  -path './.git' -prune -o \
  -path '*/build' -prune -o \
  -path '*/node_modules' -prune -o \
  -path '*/.nuxt' -prune -o \
  -path '*/.output' -prune -o \
  -type f \( -name '*.class' -o -name '*.pyc' -o -name '.DS_Store' \) \
  -print -quit | rg -q '.'; then
  echo "Generated junk exists outside ignored build directories" >&2
  exit 1
fi

# Plain `git diff --check` only inspects the diff between the working tree
# and the index — in a clean CI checkout (working tree == index == HEAD) or
# this repository's current all-untracked, zero-commit state (nothing is
# staged, so the index is empty), that diff is always empty and the check
# passes trivially without inspecting a single line of real content. Staging
# everything and diffing against the empty tree object (git's well-known
# constant for "no content") instead makes every tracked/trackable line show
# up as an addition, so whitespace-error detection actually runs against the
# full file contents. The index snapshot-and-restore keeps this
# non-destructive: whatever was staged before this script ran (or wasn't) is
# back exactly as it was once the check completes, success or failure.
empty_tree_object='4b825dc642cb6eb9a060e54bf8d69288fbee4904'
if ! original_index_tree="$(git write-tree 2>&1)"; then
  echo "Could not snapshot the current git index; aborting before staging anything: $original_index_tree" >&2
  exit 1
fi

whitespace_check_status=0
git add -A -- .
git diff --check "$empty_tree_object" --cached || whitespace_check_status=$?
git read-tree "$original_index_tree"

if (( whitespace_check_status != 0 )); then
  echo "Whitespace errors found (see above) — inspect with: git diff --check $empty_tree_object -- <path>" >&2
  exit 1
fi

echo "Repository structure and execution-board invariants are valid"
