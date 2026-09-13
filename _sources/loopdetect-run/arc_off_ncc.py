# Archive(NBS命名, 用户称真原版) vs Mojang CDN 官方文件：音频级 NCC 比对
# 目的：① 判定 Archive 是"同音频不同编码"还是"不同录音" ② 验证 NBS名↔原版名 映射
import sys
import numpy as np
import soundfile as sf

ARC = r'C:\01_Self_Software\NBSstudio\Minecraft Note Block Studio\Data\Sounds\Archive'
OFF = r'C:\00_Data\RGM\NoteGT\_sources\vanilla-cdn-26.2'

# NBS名 -> 官方文件（映射假设，由 NCC 验证）
PAIRS = [
    ('banjo', 'banjo'),
    ('bdrum', 'bd'),
    ('bell', 'bell'),
    ('bit', 'bit'),
    ('click', 'hat'),
    ('cow_bell', 'cow_bell'),
    ('dbass', 'bassattack'),
    ('didgeridoo', 'didgeridoo'),
    ('flute', 'flute'),
    ('guitar', 'guitar'),
    ('harp', 'harp2'),
    ('icechime', 'icechime'),
    ('iron_xylophone', 'iron_xylophone'),
    ('pling', 'pling'),
    ('sdrum', 'snare'),
    ('xylobone', 'xylobone'),
    ('trumpet', 'trumpet'),
    ('trumpet_exposed', 'trumpet_exposed'),
    ('trumpet_oxidized', 'trumpet_oxidized'),
    ('trumpet_weathered', 'trumpet_weathered'),
]


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
    for lag in range(-max_lag, max_lag + 1, 4):
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


print(f"{'NBS名':<18s} {'官方文件':<18s} {'sr(arch/off)':<14s} {'dur(arch/off)':<16s} {'NCC':>7s}")
for an, on in PAIRS:
    xa, sra = load(ARC + '\\' + an + '.ogg')
    xo, sro = load(OFF + '\\' + on + '.ogg')
    c = ncc(xa, xo)
    print(f'{an:<18s} {on:<18s} {sra}/{sro:<8d} {len(xa)/sra:.3f}/{len(xo)/sro:.3f}s {c:7.4f}')
