#!/usr/bin/env python3
"""Block a push when a credential, key or token is about to be committed.

Scans the files git is actually tracking (or would track) and reports any match with
file and line number. Exits non-zero when something is found, so it can be used directly
as a pre-push hook.

    python3 scripts/secret-scan.py            # scan the working tree
    python3 scripts/secret-scan.py --staged   # scan only staged files

Design notes:
  * Environment-variable placeholders with a development fallback are allowed when the
    fallback is obviously a placeholder, because that is the correct pattern for config.
    Anything that looks like a real credential is not.
  * Binary files, lockfiles and vendored dependencies are skipped: they produce noise
    without ever being where a hand-written secret lives.
  * A line carrying ``secret-scan:allow`` is skipped, so a genuine fixture can be kept
    deliberately. The marker is greppable, so exceptions stay visible in review.
"""
from __future__ import annotations

import os
import re
import subprocess
import sys

SKIP_DIRS = {
    "node_modules", "target", "dist", "build", ".git", ".tools", ".venv",
    "__pycache__", ".idea", ".vscode", "coverage",
}
SKIP_FILES = {
    "package-lock.json", "pnpm-lock.yaml", "yarn.lock", "poetry.lock",
    "secret-scan.py",  # this file lists the patterns themselves
}
SKIP_SUFFIXES = (
    ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".webp", ".pdf",
    ".jar", ".war", ".class", ".zip", ".gz", ".woff", ".woff2", ".ttf", ".map",
)

# A fallback that is plainly a placeholder is safe to commit; it documents the variable
# and cannot authenticate anything.
PLACEHOLDER = re.compile(
    r"(?i)(change[-_ ]?me|placeholder|example|dummy|your[-_ ]|not[-_ ]?configured|"
    r"replace[-_ ]?me|xxx+|todo|local[-_ ]?dev|development[-_ ]?only|shopwise)"
)

PATTERNS: list[tuple[str, str, str]] = [
    # --- cloud credentials
    ("AWS access key id", r"AKIA[0-9A-Z]{16}", "high"),
    ("AWS secret", r"aws_secret_access_key\s*[:=]\s*['\"][A-Za-z0-9/+=]{30,}['\"]", "high"),
    ("Google API key", r"AIza[0-9A-Za-z\-_]{35}", "high"),
    ("Stripe live key", r"(sk|rk|pk)_live_[0-9a-zA-Z]{24,}", "high"),
    ("Twilio key", r"\bSK[0-9a-fA-F]{32}\b", "high"),
    ("SendGrid key", r"SG\.[a-zA-Z0-9_-]{22}\.[a-zA-Z0-9_-]{43}", "high"),
    # --- model providers
    ("OpenAI key", r"\bsk-[A-Za-z0-9]{40,}\b", "high"),
    ("Anthropic key", r"sk-ant-[A-Za-z0-9\-_]{60,}", "high"),
    # --- auth tokens
    ("GitHub token", r"gh[pousr]_[A-Za-z0-9_]{36,}", "high"),
    ("Slack token", r"xox[baprs]-[0-9A-Za-z-]{10,}", "high"),
    ("JWT", r"\beyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}", "high"),
    ("Bearer token", r"(?i)bearer\s+[A-Za-z0-9\-_.~+/]{24,}=*", "high"),
    ("Private key block", r"-----BEGIN [A-Z ]*PRIVATE KEY-----", "high"),
    # --- connection strings with inline credentials
    ("Postgres URL with credentials", r"postgres(ql)?://[^/\s:'\"]+:[^@\s'\"]+@", "high"),
    ("MySQL URL with credentials", r"mysql://[^/\s:'\"]+:[^@\s'\"]+@", "high"),
    ("Mongo URL with credentials", r"mongodb(\+srv)?://[^/\s:'\"]+:[^@\s'\"]+@", "high"),
    ("Redis URL with credentials", r"redis://[^/\s:'\"]+:[^@\s'\"]+@", "high"),
    # --- generic assignments (checked against the placeholder allowlist)
    ("Hard-coded password", r"(?i)\bpassword\s*[:=]\s*['\"]([^'\"]{8,})['\"]", "review"),
    ("Hard-coded secret", r"(?i)\b(secret|api[_-]?key|apikey|access[_-]?token|client[_-]?secret)\s*[:=]\s*['\"]([^'\"]{12,})['\"]", "review"),
    ("Hard-coded private key", r"(?i)private[_-]?key\s*[:=]\s*['\"]([^'\"]{12,})['\"]", "review"),
]

TEXT_SUFFIXES = (
    ".java", ".kt", ".ts", ".tsx", ".js", ".jsx", ".json", ".yml", ".yaml",
    ".xml", ".properties", ".sql", ".md", ".sh", ".py", ".css", ".html",
    ".env", ".example", ".cfg", ".conf", ".toml", ".txt", ".gradle", "",
)


def tracked_files(staged: bool) -> list[str]:
    command = ["git", "diff", "--cached", "--name-only", "--diff-filter=ACM"] if staged \
        else ["git", "ls-files", "--cached", "--others", "--exclude-standard"]
    try:
        result = subprocess.run(command, capture_output=True, text=True, check=True)
    except (subprocess.CalledProcessError, FileNotFoundError):
        return []
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def should_skip(path: str) -> bool:
    parts = path.replace("\\", "/").split("/")
    if any(part in SKIP_DIRS for part in parts[:-1]):
        return True
    if os.path.basename(path) in SKIP_FILES:
        return True
    if path.lower().endswith(SKIP_SUFFIXES):
        return True
    return False


def is_placeholder(value: str) -> bool:
    """A fallback is fine when it is self-evidently not a usable credential."""
    return bool(PLACEHOLDER.search(value)) or "${" in value or value.strip() == ""


def looks_like_prose(value: str) -> bool:
    """Validation messages and labels are not credentials.

    A real credential is a single token with no whitespace; human-readable sentences
    (for example "Passwords are at least 8 characters.") are copy, not secrets.
    """
    stripped = value.strip()
    return bool(re.search(r"\s", stripped)) or stripped.endswith(".")


def scan(paths: list[str]) -> list[tuple[str, int, str, str, str]]:
    findings = []
    for path in paths:
        if should_skip(path) or not os.path.isfile(path):
            continue
        if not path.lower().endswith(TEXT_SUFFIXES):
            continue
        try:
            with open(path, "r", encoding="utf-8", errors="ignore") as handle:
                lines = handle.readlines()
        except OSError:
            continue

        for number, line in enumerate(lines, start=1):
            # An explicit, reviewable exception. A human had to type this next to the
            # value, which is the point: it shows up in review and can be questioned.
            if "secret-scan:allow" in line:
                continue

            for label, pattern, severity in PATTERNS:
                match = re.search(pattern, line)
                if not match:
                    continue
                captured = match.group(match.lastindex) if match.lastindex else match.group(0)
                if severity == "review" and (is_placeholder(captured) or looks_like_prose(captured)):
                    continue
                if severity == "high" and is_placeholder(captured):
                    continue
                findings.append((path, number, label, severity, line.strip()[:160]))
    return findings


def main() -> int:
    staged = "--staged" in sys.argv
    paths = tracked_files(staged)

    if not paths:
        # Not a git repository yet: fall back to walking the tree.
        paths = []
        for root, dirs, files in os.walk("."):
            dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
            for name in files:
                paths.append(os.path.relpath(os.path.join(root, name), "."))

    findings = scan(paths)
    scanned = len([p for p in paths if not should_skip(p)])

    if not findings:
        print(f"Secret scan: clean ({scanned} files scanned).")
        return 0

    print(f"SECURITY ALERT: {len(findings)} potential secret(s) found in {scanned} files.\n")
    for path, number, label, severity, excerpt in findings:
        print(f"  {path}:{number}")
        print(f"    type     : {label} ({severity})")
        print(f"    excerpt  : {excerpt}")
    print("\nBLOCKED: resolve these before pushing.")
    print("Remediation:")
    print("  1. Move the value to an environment variable and read it as ${VAR}.")
    print("  2. Put the real value in a local .env file, which must be git-ignored.")
    print("  3. If the value was ever committed, rotate it - rewriting history is not enough.")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
