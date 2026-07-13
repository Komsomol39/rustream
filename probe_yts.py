import json, urllib.request, urllib.parse
UA="Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/124 Safari/537.36"
FAKE="F998565DEB6A3E33E3F0AD40E3C8D62ED386F07A"
# ТОЧНЫЙ запрос приложения
for host in ["movies-api.accel.li","yts.bz"]:
    url=("https://%s/api/v2/list_movies.json?query_term=%s&limit=50&sort_by=seeds"
         %(host,urllib.parse.quote("sting")))
    print("### %s"%host)
    try:
        d=json.loads(urllib.request.urlopen(urllib.request.Request(url,headers={"User-Agent":UA}),timeout=25).read())
        fakefound=False
        for m in d.get("data",{}).get("movies",[]):
            if m.get("year")==2024 and m.get("title")=="Sting":
                for t in m.get("torrents",[]):
                    h=t.get("hash","").upper()
                    if h==FAKE: fakefound=True
                    print("   %-6s %-5s %s"%(t.get("quality"),t.get("type"),h))
        print("   >>> ФЕЙК f998565 присутствует: %s"%fakefound)
    except Exception as e:
        print("   FAIL",str(e)[:50])
