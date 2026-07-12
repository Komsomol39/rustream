"""Probe: все качества The Sting по каждому зеркалу — hash, size, url, реальный размер торрента."""
import json, urllib.request, urllib.error, urllib.parse

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
MIRRORS = ["yts.bz", "yts.am", "yts.lt"]

def fetch(url, timeout=25):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, r.read()

def bdecode(data, pos=0):
    c = data[pos:pos+1]
    if c == b'd':
        pos += 1; out = {}
        while data[pos:pos+1] != b'e':
            k, pos = bdecode(data, pos); v, pos = bdecode(data, pos); out[k] = v
        return out, pos+1
    if c == b'l':
        pos += 1; out = []
        while data[pos:pos+1] != b'e':
            v, pos = bdecode(data, pos); out.append(v)
        return out, pos+1
    if c == b'i':
        e = data.index(b'e', pos); return int(data[pos+1:e]), e+1
    colon = data.index(b':', pos); n = int(data[pos:colon]); s = colon+1
    return data[s:s+n], s+n

def tsize(raw):
    d, _ = bdecode(raw)
    info = d[b'info']
    if b'files' in info:
        return sum(f[b'length'] for f in info[b'files'])
    return info[b'length']

def human(n):
    for u, d in (("ГБ", 1<<30), ("МБ", 1<<20), ("КБ", 1<<10)):
        if n >= d: return "%.1f %s" % (n/d, u)
    return "%d Б" % n

for host in MIRRORS:
    print("\n" + "="*55)
    print("### %s" % host)
    try:
        _, body = fetch("https://%s/api/v2/list_movies.json?query_term=%s&limit=3"
                        % (host, urllib.parse.quote("the sting 1973")))
        data = json.loads(body)
        movies = data.get("data", {}).get("movies") or []
    except Exception as e:
        print("  API fail:", str(e)[:70]); continue

    for m in movies:
        if m.get("year") != 1973: continue
        print("\n  ФИЛЬМ: %s (%s)" % (m.get("title"), m.get("year")))
        for t in m.get("torrents", []):
            q = t.get("quality"); ty = t.get("type")
            h = t.get("hash"); exp = t.get("size_bytes", 0)
            url = t.get("url")
            print("\n    [%s %s] hash=%s" % (q, ty, h))
            print("      API size: %s | url: %s" % (human(exp), url))
            for cand in [url, "https://%s/torrent/download/%s" % (host, h)]:
                if not cand: continue
                try:
                    st, raw = fetch(cand)
                    if raw[:1] != b'd':
                        print("      %s -> HTTP %s НЕ torrent" % (cand[:60], st)); continue
                    act = tsize(raw)
                    ok = "НАСТОЯЩИЙ" if act > exp*0.5 else "ФЕЙК"
                    print("      %s\n        -> %s (в торренте %s)" % (cand[:70], ok, human(act)))
                except urllib.error.HTTPError as e:
                    print("      %s -> HTTP %s" % (cand[:70], e.code))
                except Exception as e:
                    print("      %s -> err %s" % (cand[:70], str(e)[:40]))
