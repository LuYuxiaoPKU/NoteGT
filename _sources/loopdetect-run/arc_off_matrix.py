# 全矩阵 NCC：每个 Archive 文件 vs 全部官方文件（含遗留 bass/harp），报 top-2
import glob
import os
import numpy as np
import soundfile as sf

ARC = r'C:\01_Self_Software\NBSstudio\Minecraft Note Block Studio\Data\Sounds\Archive'
OFF = r'C:\00_Data\RGM\NoteGT\_sources\vanilla-cdn-26.2'


def load(p):
    x, sr = sf.read(p, dtype='float64')
    if x.ndim > 1:
        x = x.mean(axis=1)
    return x, sr


def ncc(a, b, max_lag=400):
    n = min(len(a), len(b))
    a = a[:n]; b = b[:n]
    a = (a - a.mean()) / (np.sqrt(np.mean(a ** 2)) + 1e-12)
    b = (b - b.mean()) / (np.sqrt(np.mean(b ** 2)) + 1e-12)
    best, bl = -2, 0
    for lag in range(-max_lag, max_lag + 1, 8):
        if lag >= 0:
            va, vb = a[lag:], b[:len(b) - lag]
        else:
            va, vb = a[:lag], b[-lag:]
        m = min(len(va), len(vb))
        if m < 2000:
            continue
        c = float(np.dot(va[:m], vb[:m]) / m)
        if c > best:
            best, bl = c, lag
    return best


arc_files = sorted(os.path.basename(p)[:-4] for p in glob.glob(ARC + '\\*.ogg'))
off_files = sorted(os.path.basename(p)[:-4] for p in glob.glob(OFF + '\\*.ogg'))
off_data = {f: load(os.path.join(OFF, f + '.ogg')) for f in off_files}

print(f"Archive文件  | top1(官方: NCC)                | top2(官方: NCC)               | 判定")
print('-' * 92)
for af in arc_files:
    xa, sra = load(os.path.join(ARC, af + '.ogg'))
    scored = []
    for of in off_files:
        xo, sro = off_data[of]
        c = ncc(xa, xo)
        scored.append((c, of, sra, sro))
    scored.sort(reverse=True)
    (c1, f1, sra, sro1), (c2, f2, _, sro2) = scored[0], scored[1]
    verdict = '同音频(重编码)' if c1 >= 0.95 else ('不同录音' if c1 < 0.9 else '存疑')
    print(f'{af:<14s} | {f1}: {c1:.4f} (sr {sra}/{sro1})  | {f2}: {c2:.4f} (sr {sro2})  | {verdict}')
