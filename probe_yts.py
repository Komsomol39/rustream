import json, urllib.request, urllib.parse
UA=("Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/124 Safari/537.36")
FAKE="F998565DEB6A3E33E3F0AD40E3C8D62ED386F07A"
# Ищем именно Sting 2024
u="https://yts.bz/api/v2/list_movies.json?query_term=%s&limit=50"%urllib.parse.quote("sting")
d=json.loads(urllib.request.urlopen(urllib.request.Request(u,headers={"User-Agent":UA}),timeout=25).read())
for m in d.get("data",{}).get("movies",[]):
    if m.get("year")==2024:
        print("НАЙДЕН: %s (%s)"%(m.get("title"),m.get("year")))
        for t in m.get("torrents",[]):
            h=t.get("hash","").upper()
            print("  %-6s %s hash=%s %s"%(t.get("quality"),t.get("size"),h,
                  "<<< ЭТО ОН, фейк-хэш" if h==FAKE else ""))
            print("       url: %s"%t.get("url"))
