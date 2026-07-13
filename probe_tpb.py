import json, urllib.request, urllib.parse
UA="Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/124 Safari/537.36"
for q in ["sting 2024","the matrix 1999"]:
    url="https://apibay.org/q.php?q=%s&cat=0"%urllib.parse.quote(q)
    print("### q=%s"%q)
    try:
        d=json.loads(urllib.request.urlopen(urllib.request.Request(url,headers={"User-Agent":UA}),timeout=25).read())
        print("  результатов:", len(d))
        for o in d[:4]:
            print("  name=%s" % o.get("name","")[:55])
            print("    info_hash=%s seeders=%s size=%s cat=%s" %
                  (o.get("info_hash"),o.get("seeders"),o.get("size"),o.get("category")))
        # ключи первого объекта — сверить с парсером
        if d: print("  КЛЮЧИ:", sorted(d[0].keys()))
    except Exception as e:
        print("  FAIL", str(e)[:60])
