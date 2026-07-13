"""Есть ли F998565D среди торрентов The Sting? Полный дамп всех торрентов."""
import json, urllib.request, urllib.parse
UA=("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
FAKE="F998565DEB6A3E33E3F0AD40E3C8D62ED386F07A"

# Точно как приложение: sort_by=seeds, limit=50
url=("https://yts.bz/api/v2/list_movies.json?query_term=%s&limit=50&sort_by=seeds"
     % urllib.parse.quote("the sting"))
r=urllib.request.Request(url,headers={"User-Agent":UA})
d=json.loads(urllib.request.urlopen(r,timeout=25).read())
print("movies:", d["data"].get("movie_count"))
for m in d.get("data",{}).get("movies",[]):
    print("\n%s (%s) id=%s" % (m.get("title"), m.get("year"), m.get("id")))
    for t in m.get("torrents",[]):
        h=t.get("hash","").upper()
        mark=" <<< ЭТО ФЕЙК-ХЭШ ИЗ ПРИЛОЖЕНИЯ" if h==FAKE else ""
        print("   %-6s %-7s %s  %s%s" % (t.get("quality"),t.get("type"),
              t.get("size"), h, mark))

# Прямо ищем фейк по всей выдаче
print("\n=== поиск F998565D по всем фильмам 'sting' (разные запросы) ===")
for q in ["sting","the sting","sting 2024","sting operation"]:
    u=("https://yts.bz/api/v2/list_movies.json?query_term=%s&limit=50"
       % urllib.parse.quote(q))
    try:
        dd=json.loads(urllib.request.urlopen(urllib.request.Request(u,headers={"User-Agent":UA}),timeout=25).read())
        for m in dd.get("data",{}).get("movies",[]):
            for t in m.get("torrents",[]):
                if t.get("hash","").upper()==FAKE:
                    print("  НАЙДЕН в '%s': %s (%s) %s %s" %
                          (q,m.get("title"),m.get("year"),t.get("quality"),t.get("size")))
    except Exception as e:
        print("  '%s' err %s"%(q,str(e)[:40]))
print("(если выше пусто — фейк-хэша в каталоге YTS НЕТ вообще)")
