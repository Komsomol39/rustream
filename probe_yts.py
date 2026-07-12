"""Проверяем ОБА хэша: настоящий ли F998565D, существует ли он, что за ним."""
import json, urllib.request, urllib.parse

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
HASH_APP  = "F998565DEB6A3E33E3F0AD40E3C8D62ED386F07A"  # что использует телефон
HASH_MINE = "092830915ADEA71C92FA58DF2E8EB39EA3CF3449"  # что вижу я

def get(url, timeout=25):
    r = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(r, timeout=timeout) as resp:
        return resp.status, resp.read()

def bdecode(d, p=0):
    c=d[p:p+1]
    if c==b'd':
        p+=1;o={}
        while d[p:p+1]!=b'e':
            k,p=bdecode(d,p);v,p=bdecode(d,p);o[k]=v
        return o,p+1
    if c==b'l':
        p+=1;o=[]
        while d[p:p+1]!=b'e':
            v,p=bdecode(d,p);o.append(v)
        return o,p+1
    if c==b'i':
        e=d.index(b'e',p);return int(d[p+1:e]),e+1
    cn=d.index(b':',p);n=int(d[p:cn]);s=cn+1;return d[s:s+n],s+n

def tsize(raw):
    info=bdecode(raw)[0][b'info']
    return sum(f[b'length'] for f in info[b'files']) if b'files' in info else info[b'length']

# 1. Ищет ли API вообще этот фильм, и какие хэши у 2160p на разных хостах
print("=== какие 2160p-хэши отдаёт API на каждом хосте ===")
for host in ["movies-api.accel.li","yts.bz","yts.am","yts.lt","yts.mx"]:
    try:
        _,b=get("https://%s/api/v2/list_movies.json?query_term=%s&limit=5"%(host,urllib.parse.quote("the sting")))
        d=json.loads(b)
        for m in d.get("data",{}).get("movies",[]):
            if m.get("year")==1973 and "Sting" in m.get("title",""):
                for t in m.get("torrents",[]):
                    if t.get("quality")=="2160p":
                        print("  %-22s 2160p hash=%s" % (host, t.get("hash","").upper()))
    except Exception as e:
        print("  %-22s FAIL %s" % (host, str(e)[:40]))

# 2. Существуют ли оба хэша как скачиваемый .torrent
print("\n=== проверка обоих хэшей на скачиваемость ===")
for label,h in [("APP  F998565D",HASH_APP),("MINE 092830915A",HASH_MINE)]:
    for host in ["yts.gg","yts.bz","itorrents.org"]:
        if host=="itorrents.org":
            url="https://itorrents.org/torrent/%s.torrent"%h
        else:
            url="https://%s/torrent/download/%s"%(host,h)
        try:
            st,raw=get(url)
            if raw[:1]==b'd':
                print("  [%s] %s -> HTTP %s, размер %d МБ" % (label,host,st,tsize(raw)//(1<<20)))
            else:
                print("  [%s] %s -> HTTP %s, не torrent" % (label,host,st))
        except Exception as e:
            print("  [%s] %s -> %s" % (label,host,str(e)[:40]))
