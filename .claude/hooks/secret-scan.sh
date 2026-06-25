#!/usr/bin/env bash
# PreToolUse — Bash (git commit). Scans staged content for secrets. Fail-closed on parse.
INPUT=$(cat)
. "$(dirname "$0")/_lib.sh"

guard_require command
CMD="$(json_field command)"
echo "$CMD" | grep -qE 'git\s+commit' || exit 0
git rev-parse HEAD >/dev/null 2>&1 || exit 0
[ -z "$(git diff --staged 2>/dev/null)" ] && exit 0
[ -z "$_PY" ] && guard_block "Blocked: cannot scan staged diff for secrets (no python). Review manually or install python."

"$_PY" - << 'PYEOF'
import subprocess, sys, re
diff = subprocess.run(["git","diff","--staged"],capture_output=True,text=True).stdout
patterns = {
    "AWS Access Key": r'AKIA[0-9A-Z]{16}',
    "AWS Secret Key": r'(?i)aws.{0,20}secret.{0,20}["\']?[A-Za-z0-9/+=]{40}',
    "Private key header": r'-----BEGIN (RSA|EC|DSA|OPENSSH) PRIVATE KEY-----',
    "JWT token": r'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}',
    "Password in YAML": r'(?i)(password|passwd|secret|credentials?)\s*[:=]\s*["\']?(?!(\$\{|<|your|change|example|placeholder|test|dummy))[A-Za-z0-9!@#$%^&*]{8,}',
    "DB connection string": r'(?i)(jdbc:[a-z]+://[^"\';\s]*:[^"\';\s@]+@)',
    "Generic API key": r'(?i)(api[_-]?key|apikey|api[_-]?secret)\s*[:=]\s*["\']?[A-Za-z0-9_\-]{16,}',
    "Spring datasource password": r'(?i)spring\.datasource\.password\s*[:=]\s*(?!(\$\{|<))\S+',
}
hits=[]
for name,pat in patterns.items():
    for m in re.finditer(pat,diff):
        ln=diff[:m.start()].count("\n")+1
        hits.append(f"  [{name}] line ~{ln}: {m.group()[:60]}...")
if hits:
    sys.stderr.write("Blocked: potential secrets in staged changes:\n"+"\n".join(hits)+"\nRemove secrets before committing.\n")
    sys.exit(2)
PYEOF
exit $?
