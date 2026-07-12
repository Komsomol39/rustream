"""
Диагностика YTS: какие зеркала живы, отдают ли они НАСТОЯЩИЕ хэши
(а не клон-фейки с рекламой), и по какому URL реально качается .torrent.

Проверка подлинности: скачиваем .torrent, bdecode'им, считаем суммарный
размер файлов и сравниваем с size_bytes из API. У фейка это будут
десятки КБ вместо гигабайтов.
"""
import json
import urllib.request
import urllib.error
import urllib.parse

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")

MIRRORS = ["yts.mx", "yts.bz", "yts.rs", "yts.am", "yts.lt", "yts.do"]
QUERY = "the sting 1973"


def fetch(url, timeout=20):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, r.read()


def bdecode(data, pos=0):
    """Минимальный bencode-декодер, чтобы не тянуть зависимости."""
    c = data[pos:pos + 1]
    if c == b'd':
        pos += 1
        out = {}
        while data[pos:pos + 1] != b'e':
            k, pos = bdecode(data, pos)
            v, pos = bdecode(data, pos)
            out[k] = v
        return out, pos + 1
    if c == b'l':
        pos += 1
        out = []
        while data[pos:pos + 1] != b'e':
            v, pos = bdecode(data, pos)
            out.append(v)
        return out, pos + 1
    if c == b'i':
        end = data.index(b'e', pos)
        return int(data[pos + 1:end]), end + 1
    colon = data.index(b':', pos)
    n = int(data[pos:colon])
    start = colon + 1
    return data[start:start + n], start + n


def torrent_total_size(raw):
    d, _ = bdecode(raw)
    info = d[b'info']
    if b'files' in info:
        return sum(f[b'length'] for f in info[b'files'])
    return info[b'length']


def human(n):
    for unit, div in (("ГБ", 1 << 30), ("МБ", 1 << 20), ("КБ", 1 << 10)):
        if n >= div:
            return "%.1f %s" % (n / div, unit)
    return "%d Б" % n


print("=" * 60)
print("ЗАПРОС:", QUERY)
print("=" * 60)

for host in MIRRORS:
    print("\n### %s" % host)
    api = ("https://%s/api/v2/list_movies.json?query_term=%s&limit=1"
           % (host, urllib.parse.quote(QUERY)))
    try:
        status, body = fetch(api)
        data = json.loads(body)
        movies = data.get("data", {}).get("movies") or []
        if not movies:
            print("  API отвечает (HTTP %s), но фильмов не найдено" % status)
            continue
        movie = movies[0]
        tor = movie["torrents"][0]
        h = tor["hash"]
        expected = tor.get("size_bytes", 0)
        print("  API OK: %s (%s)" % (movie["title"], tor.get("quality")))
        print("  hash     = %s" % h)
        print("  API size = %s" % human(expected))
        print("  API url  = %s" % tor.get("url"))
    except Exception as e:
        print("  API НЕДОСТУПЕН: %s" % str(e)[:80])
        continue

    # Пробуем скачать .torrent разными способами
    candidates = [
        tor.get("url"),
        "https://%s/torrent/download/%s" % (host, h),
        "https://%s/torrent/download/%s.torrent" % (host, h),
    ]
    for url in candidates:
        if not url:
            continue
        try:
            status, raw = fetch(url)
            if raw[:1] != b'd':
                print("  [%s] -> HTTP %s, но это НЕ torrent (%r...)"
                      % (url, status, raw[:20]))
                continue
            actual = torrent_total_size(raw)
            verdict = "НАСТОЯЩИЙ" if actual > expected * 0.5 else "ФЕЙК!"
            print("  [%s]\n     -> HTTP %s, размер в торренте = %s  => %s"
                  % (url, status, human(actual), verdict))
        except urllib.error.HTTPError as e:
            print("  [%s] -> HTTP %s" % (url, e.code))
        except Exception as e:
            print("  [%s] -> ошибка: %s" % (url, str(e)[:60]))
