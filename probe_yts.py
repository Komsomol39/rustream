"""Проверяем варианты обхода SNI-перехвата для yts.bz."""
import json, socket, ssl, urllib.request, urllib.parse

UA=("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
GENUINE="092830915ADEA71C92FA58DF2E8EB39EA3CF3449"

def check(host, note):
    url=("https://%s/api/v2/list_movies.json?query_term=%s&limit=5"
         %(host,urllib.parse.quote("the sting")))
    try:
        r=urllib.request.Request(url,headers={"User-Agent":UA})
        d=json.loads(urllib.request.urlopen(r,timeout=20).read())
        for m in d.get("data",{}).get("movies",[]):
            if m.get("year")==1973 and "Sting" in m.get("title",""):
                for t in m.get("torrents",[]):
                    if t.get("quality")=="2160p":
                        hh=t.get("hash","").upper()
                        print("  %-28s hash=%s %s" % (note, hh,
                              "OK" if hh==GENUINE else "ФЕЙК"))
                        return
        print("  %-28s фильм не найден" % note)
    except Exception as e:
        print("  %-28s FAIL %s" % (note, str(e)[:45]))

print("=== разные хосты/пути к YTS API ===")
for h,note in [("yts.bz","yts.bz"),
               ("movies-api.accel.li","accel.li (офиц API)"),
               ("en.yts-official.mx","yts-official.mx"),
               ("yts.rs","yts.rs"),
               ("yts.hn","yts.hn"),
               ("yts.pm","yts.pm")]:
    check(h,note)

print("\n=== публичные YTS-совместимые API (другие домены целиком) ===")
for h,note in [("yts.torrentbay.st","torrentbay"),
               ("yify.unblockit.foo","unblockit")]:
    check(h,note)
