import json, sys
d = r'C:\00_Data\RGM\NoteGT\_sources\loopdetect-run'
for tag in sys.argv[1:]:
    rows = json.load(open(f'{d}\\{tag}', encoding='utf-8-sig'))
    print('==', tag, f'({len(rows)} files)')
    for r in rows:
        name = r['file'].split('\\')[-1]
        if not r.get('ok'):
            print(f"  {name:24s} REJECT: {r.get('reason')}")
        else:
            click = r.get('seamClickDb')
            phase = r.get('seamPhaseDeg')
            # L2 结果导向四条件（Hy4 第四轮）：loop>=150ms & ripple<=2 & mod<=-40 & (click<=-40 & phase<10)
            c1 = r['loopMs'] >= 150
            c2 = r['longRippleDb'] <= 2.0
            c3 = r['modDbc'] <= -40
            c4 = (click is not None and phase is not None and click <= -40 and phase < 10)
            l2 = 'L2-OK' if (c1 and c2 and c3 and c4) else f"L2-FAIL[{int(c1)}{int(c2)}{int(c3)}{int(c4)}]"
            print(f"  {name:24s} f0={r['f0']:<8} src={r['f0Source']:<14} k={r['periods']:<4} "
                  f"loop={r['loopMs']:<8} ncc={r['ncc']:<7} seam={r['seamRatio']:<6} "
                  f"click={str(click):<7} phase={str(phase):<7} "
                  f"ripple={r['longRippleDb']:<6} mod={r['modDbc']:<8} {r['verdict']:4s} {l2}")
