#!/usr/bin/env python3
"""Nudge (non-blocking-safe) reminder when a Write/Edit leaves a public top-level
class/interface/enum in a .java file without a class-level Javadoc comment.
Reads the PostToolUse hook payload from stdin; writes a hookSpecificOutput
JSON to stdout only when something is missing, so it stays silent otherwise.
"""
import json
import re
import sys

DECL_RE = re.compile(r'^(public\s+)?(class|interface|enum|@interface)\s+(\w+)')


def has_preceding_javadoc(lines, decl_index):
    i = decl_index - 1
    while i >= 0:
        line = lines[i].strip()
        if line == '':
            i -= 1
            continue
        if line.startswith('@'):
            i -= 1
            continue
        return line.endswith('*/')
    return False


def find_undocumented_types(path):
    try:
        with open(path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
    except OSError:
        return []

    missing = []
    for idx, raw_line in enumerate(lines):
        if raw_line.startswith((' ', '\t')):
            continue  # only top-level (column 0) declarations
        m = DECL_RE.match(raw_line)
        if m and not has_preceding_javadoc(lines, idx):
            missing.append(m.group(3))
    return missing


def main():
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        return

    file_path = (payload.get('tool_input') or {}).get('file_path', '')
    if not file_path.endswith('.java'):
        return

    missing = find_undocumented_types(file_path)
    if not missing:
        return

    names = ', '.join(missing)
    print(json.dumps({
        'hookSpecificOutput': {
            'hookEventName': 'PostToolUse',
            'additionalContext': (
                f"Reminder (project convention in CLAUDE.md): {file_path} is missing a "
                f"class-level Javadoc comment for: {names}. Add one before finishing this task."
            ),
        }
    }))


if __name__ == '__main__':
    main()