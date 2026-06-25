#!/usr/bin/env bash
# PreToolUse — Read|Write|Edit|Bash. Blocks tool access to secrets files. Fail-closed.
#
# Path-based tools (Read|Write|Edit): the file_path IS the target — match it directly.
# Bash: match only path-like *tokens*, never the whole command line. Prose in commit
# messages / heredoc bodies must not trip a file guard; secret *content* in a commit is
# caught by secret-scan.sh (it scans the staged diff), so git commit/tag/merge/stash are
# skipped here to avoid false positives on their message text.
INPUT=$(cat)
. "$(dirname "$0")/_lib.sh"

guard_require file_path   # fail-closed if a key is present but unparseable
guard_require command
FILE_PATH="$(json_field file_path)"
CMD="$(json_field command)"

PATTERNS=(
  '\.env(\.|$)' '\.pem$' '\.key$' '\.p12$' '\.pfx$' '\.jks$' '\.der$'
  '(^|[^a-z])secrets?\.(ya?ml|json|properties|env|conf|cfg|toml|txt|enc|p12|pfx|jks|der)'
  '(^|[/.-])credentials?(\.|$)' 'id_rsa' 'id_ed25519' 'id_ecdsa'
  'application-prod(uction)?\.ya?ml'
)

# matches_secret <string> — return 0 if the string matches any secrets pattern.
matches_secret() {
  local s="$1" p
  for p in "${PATTERNS[@]}"; do
    printf '%s' "$s" | grep -qiE "$p" && return 0
  done
  return 1
}

# Path-based tools: the file_path is the target — match it directly.
if [ -n "$FILE_PATH" ] && matches_secret "$FILE_PATH"; then
  guard_block "Blocked: access to a secrets/credentials file is not permitted ('$FILE_PATH'). Use env vars or a secrets manager."
fi

# Bash: scan path-like tokens only. Skip prose-bearing git subcommands.
if [ -n "$CMD" ]; then
  case "$CMD" in
    *"git commit"*|*"git tag"*|*"git merge"*|*"git stash"*) ;;  # message text is prose — not a path
    *)
      # Split on shell separators, strip one layer of surrounding quotes, test each token.
      # Trailing \n ensures the final token is read (read returns false at EOF otherwise).
      HIT="$(
        printf '%s\n' "$CMD" \
          | tr ' \t\n|;&()<>=,' '\n' \
          | while IFS= read -r raw; do
              tok="${raw%\'}"; tok="${tok#\'}"; tok="${tok%\"}"; tok="${tok#\"}"
              [ -n "$tok" ] || continue
              if matches_secret "$tok"; then printf '%s' "$tok"; break; fi
            done
      )"
      if [ -n "$HIT" ]; then
        guard_block "Blocked: command accesses a secrets/credentials file ('$HIT'). Use env vars or a secrets manager."
      fi
      ;;
  esac
fi
exit 0
