#!/usr/bin/env python3
"""
Recover the generated application source from the workflow subagent transcripts.

The scratchpad staging tree was wiped, but every worker wrote its files through the
Write tool, so the full contents survive as tool_use inputs inside the per-agent
JSONL transcripts. This replays the last write of each file into the project.

Usage:  python3 recover.py [--dry-run]
"""

import json
import os
import sys
from pathlib import Path

TRANSCRIPTS = Path(
    "/home/shivansh/.claude/projects/-home-shivansh-app/"
    "557686a2-50f3-4758-aec8-bc0a770f4f59/subagents/workflows/wf_2b5781bb-7b0"
)
STAGING_PREFIX = (
    "/tmp/claude-1000/-home-shivansh-app/"
    "557686a2-50f3-4758-aec8-bc0a770f4f59/scratchpad/staging/"
)
PROJECT = Path("/home/shivansh/app")

DRY_RUN = "--dry-run" in sys.argv


def iter_tool_uses(path):
    """Yield every tool_use block in a transcript, in order."""
    with path.open(encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                rec = json.loads(line)
            except json.JSONDecodeError:
                continue
            msg = rec.get("message") or rec
            content = msg.get("content")
            if not isinstance(content, list):
                continue
            for block in content:
                if isinstance(block, dict) and block.get("type") == "tool_use":
                    yield block


def apply_edit(text, old, new, replace_all):
    if old not in text:
        return None
    return text.replace(old, new) if replace_all else text.replace(old, new, 1)


# path -> content. Later writes win, which is what we want: the audit agents
# applied their fixes after the build agents wrote the originals.
files = {}
edit_failures = []

# Order by mtime, not filename: the audit agents ran after the build agents and
# their fixes must replay on top. Sorting by agent id would interleave them wrongly.
for tpath in sorted(TRANSCRIPTS.glob("agent-*.jsonl"), key=lambda p: p.stat().st_mtime):
    for block in iter_tool_uses(tpath):
        name = block.get("name")
        args = block.get("input") or {}
        fp = args.get("file_path")
        if not fp or not fp.startswith(STAGING_PREFIX):
            continue
        rel = fp[len(STAGING_PREFIX):]

        if name == "Write":
            files[rel] = args.get("content", "")
        elif name == "Edit":
            base = files.get(rel)
            if base is None:
                edit_failures.append((rel, "edit before any write"))
                continue
            updated = apply_edit(
                base,
                args.get("old_string", ""),
                args.get("new_string", ""),
                bool(args.get("replace_all")),
            )
            if updated is None:
                edit_failures.append((rel, "old_string not found"))
            else:
                files[rel] = updated

if not files:
    print("No recoverable files found — transcript format may differ.")
    sys.exit(1)

written = 0
for rel, content in sorted(files.items()):
    dest = PROJECT / rel
    print(f"{'would write' if DRY_RUN else 'writing'}  {rel}  ({len(content)} bytes)")
    if not DRY_RUN:
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(content, encoding="utf-8")
        written += 1

print(f"\n{len(files)} files recovered" + ("" if DRY_RUN else f", {written} written"))
if edit_failures:
    print(f"\n{len(edit_failures)} edit(s) could not be replayed:")
    for rel, why in edit_failures:
        print(f"  - {rel}: {why}")
