import json

def show(p, label):
    d = json.load(open(p, encoding='utf-16'))
    print('====', label, len(d), '====')
    for r in d:
        n = r['file'].split('\\')[-1]
        if not r.get('ok'):
            print(f"{n:24s} REJECT {r.get('reason','')}")
            continue
        print(f"{n:24s} {r['verdict']:5s} f0={r['f0']:7.2f} loop={r['loopMs']:7.1f}ms rate={r['loopRateHz']:6.2f}Hz "
              f"ncc={r['ncc']:.4f} | lp={r['loopRippleDb']:5.2f} lg={r['longRippleDb']:5.2f} mod={r['modDbc']:7.2f} "
              f"| perr={r.get('periodErrDeg')} fadeNcc={r.get('fadeNcc')} fadeDip={r.get('fadeDipDb')} xf={r.get('xfadeSamples')}")

base = r'C:\00_Data\RGM\NoteGT\_sources\loopdetect-run\\'
show(base + 'result-v4-user.json', 'USER v4 (21)')
show(base + 'result-v4-guitar-090.json', 'guitar@0.90 v4')
show(base + 'result-v4-vanilla262.json', 'vanilla 26.2 trumpets v4')
show(base + 'result-v4-vanilla12111.json', 'vanilla 1.21.11 v4 (16)')
