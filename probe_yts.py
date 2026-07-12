"""Проверка нового официального API-хоста и подлинности хэшей."""
import json, urllib.request, urllib.parse

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
GENUINE_2160 = "092830915ADEA71C92FA58DF2E8EB39EA3CF3449"
HOSTS = ["movies-api.accel.li", "yts.bz", "yts.am", "yts.lt"]

def get(url):
    r = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(r, timeout=25) as resp:
        return resp.read()

for h in HOSTS:
    print("\n### %s" % h)
    # тот же запрос, что шлёт приложение
    url = ("https://%s/api/v2/list_movies.json?query_term=%s&limit=50&sort_by=seeds"
           % (h, urllib.parse.quote("the sting")))
    try:
        data = json.loads(get(url))
    except Exception as e:
        print("  FAIL:", str(e)[:70]); continue
    if data.get("status") != "ok":
        print("  status:", data.get("status")); continue
    found = False
    for m in data.get("data", {}).get("movies", []):
        if m.get("year") == 1973 and "Sting" in m.get("title", ""):
            for t in m.get("torrents", []):
                if t.get("quality") == "2160p":
                    hh = t.get("hash", "").upper()
                    print("  2160p hash: %s" % hh)
                    print("  verdict: %s" % ("НАСТОЯЩИЙ" if hh == GENUINE_2160 else "ПОДМЕНА!"))
                    print("  api url : %s" % t.get("url"))
                    found = True
    if not found:
        print("  фильм не найден в выдаче (movies=%d)"
              % len(data.get("data", {}).get("movies", [])))
