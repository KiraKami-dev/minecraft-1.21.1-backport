"""Before/after summary of a backport conversion.
Usage: summary.py SRC_SCAN.json OUT_SCAN.json [ITEM_LOG] [BLOCK_RULES_JAVA]
Both scans should come from scan.py with SCAN_EXACT=1 (and SCAN_SKIP_PROTO=1 for the source).
Ids that appear in the substitution log / block rules are marked '*' (explained), the rest '?'."""
import sys, json

a = json.load(open(sys.argv[1])); b = json.load(open(sys.argv[2]))
known = ''
for f in sys.argv[3:]:
    try: known += open(f).read()
    except OSError: pass

def tot(d, k): return sum(d.get(k, {}).values())
print('%-18s %16s %16s %14s' % ('', 'source', 'converted', 'diff'))
for k in ('blocks', 'block_entities', 'entities', 'items', 'components', 'chunks'):
    x, y = tot(a, k), tot(b, k)
    print('%-18s %16d %16d %+14d' % (k, x, y, y - x))
print('%-18s %16d %16d' % ('players', a['entities'].get('player', 0), b['entities'].get('player', 0)))
print('%-18s %16d %16d' % ('scan errors', tot(a, 'errors'), tot(b, 'errors')))

for k, n in (('blocks', 25), ('block_entities', 15), ('entities', 15), ('items', 25), ('components', 15)):
    A, B = a.get(k, {}), b.get(k, {})
    diffs = [(i, A.get(i, 0), B.get(i, 0)) for i in set(A) | set(B) if A.get(i, 0) != B.get(i, 0)]
    diffs.sort(key=lambda t: -abs(t[1] - t[2]))
    print('\n%s: %d ids differ, largest %d (* explained by substitution tables, ? not)' % (k, len(diffs), n))
    for i, x, y in diffs[:n]:
        base = i.replace(' (sections)', '').lstrip('!')
        print('  %s %-55s %12d -> %12d' % ('*' if base in known else '?', i, x, y))
    unexplained = [t for t in diffs if t[0].replace(' (sections)', '').lstrip('!') not in known]
    print('  unexplained ids: %d' % len(unexplained))
