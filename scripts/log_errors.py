"""List the data errors of a server log, ignoring known noise (dist cleaner, refmaps, mod recipes). Usage: log_errors.py LOG"""
import re, sys, collections
NOISE = re.compile(r'DISTXFORM|RecipeManager|Mixin|mixin|refmap|MineSkin|OFFLINE/INSECURE|ServerStatsCounter|AttributeMap|JsonParseException')
c = collections.Counter(); first = {}
cur = None
for line in open(sys.argv[1], errors='replace'):
    line = re.sub(r'\x1b\[[0-9;]*m', '', line.rstrip('\n'))
    m = re.match(r'\[[\d:.]+\] \[([^\]]*)/(ERROR|FATAL)\] \[([^\]]*)\]: (.*)', line)
    if m:
        cur = None if NOISE.search(line) else line
        if cur:
            key = re.sub(r'\d+', 'N', m.group(3) + ': ' + m.group(4))[:160]
            c[key] += 1; first.setdefault(key, line[:300])
    elif cur and not line.startswith(('\tat ', '\t... ', 'Caused')) and line.strip() and False:
        pass
print('server log data errors (noise excluded): %d' % sum(c.values()))
for k, n in c.most_common(25): print('  %5d  %s' % (n, first[k]))
