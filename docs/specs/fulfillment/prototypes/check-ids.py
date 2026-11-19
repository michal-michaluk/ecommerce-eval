#!/usr/bin/env python3
"""Id-namespace gate for docs/specs/fulfillment.

Rules enforced:
  1. Every element file uses element-scoped ids only: Q-NN-n / R-NN-n / D-NN-n.
  2. A bare Qn / Rn / Dn may appear only on a line that also says `spec.md` (a reference to the global register).
  3. No scoped id is defined in more than one file, and Q-NN-n / R-NN-n / D-NN-n is
     defined only in element NN's file. A definition is a bullet whose bold lead is
     the id (`- **Q-03-1 open:**`); any other mention is a cross-element reference,
     which must name the element instead (`element 03 Q-03-1`, spec.md D6). The
     element name is looked for in the whole paragraph, so a wrapped bullet passes.
  4. Every `element NN` reference points at an existing file, and every scoped id
     mentioned anywhere is defined in exactly one file.
  5. A bare id with a letter suffix (Q11a, R7b, D2c) is rejected the same way a bare
     number is: it may appear only on a line that also says `spec.md`.
Exit 1 on any violation.
"""
import re
import sys
import glob
import collections

files = sorted(glob.glob('[0-9][0-9]-*.md'))
if not files:
    print('run me from docs/specs/fulfillment')
    sys.exit(2)

bare_ok = re.compile(r'spec\.md')
bare = re.compile(r'(?<![\w-])([QRD])(\d{1,3})(?![-\w])')
letter_suffix = re.compile(r'(?<![\w-])([QRD])(\d{1,3})([a-z]+)(?![-\w])')
scoped = re.compile(r'(?<![\w-])([QRD]-\d{2}-\d+)(?![-\w])')
definition = re.compile(r'^[ \t]*[-*][ \t]+\*\*([QRD])-(\d{2})-(\d+)')

violations = []
defs = collections.defaultdict(set)
uses = collections.defaultdict(set)
mentions = []
for f in files:
    block = []
    for lineno, line in enumerate(open(f, encoding='utf-8'), 1):
        if not line.strip():
            block = []
            continue
        block.append(line)
        for m in bare.finditer(line):
            if not bare_ok.search(line):
                violations.append((f, lineno, 'bare id %s' % m.group(0)))
        for m in letter_suffix.finditer(line):
            if not bare_ok.search(line):
                violations.append((f, lineno, 'bare id with letter suffix %s' % m.group(0)))
        for m in definition.finditer(line):
            key = '%s-%s-%s' % m.groups()
            defs[key].add(f)
            if m.group(2) != f[:2]:
                violations.append((f, lineno, 'scoped id %s defined outside element %s' % (key, m.group(2))))
        for m in scoped.finditer(line):
            uses[m.group(1)].add(f)
            mentions.append((f, lineno, m.group(1), block))
        for m in re.finditer(r'element (\d{2})\b', line):
            if not glob.glob('%s-*.md' % m.group(1)):
                violations.append((f, lineno, 'dangling element ref %s' % m.group(1)))

for key, owners in sorted(defs.items()):
    if len(owners) > 1:
        violations.append((','.join(sorted(owners)), 0, 'scoped id defined twice: %s' % key))

for key in sorted(uses):
    if key not in defs:
        violations.append((','.join(sorted(uses[key])), 0, 'scoped id used but never defined: %s' % key))

for f, lineno, key, block in mentions:
    owners = defs.get(key)
    if not owners or f in owners:
        continue
    element = key[2:4]
    if not re.search(r'element %s\b' % element, ''.join(block)):
        violations.append((f, lineno, 'cross-element id %s without "element %s"' % (key, element)))

print('files: %d  scoped ids: %d  violations: %d' % (len(files), len(defs), len(violations)))
for v in violations[:40]:
    print('  %s:%s  %s' % v)
sys.exit(1 if violations else 0)
