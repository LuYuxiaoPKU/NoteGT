import soundfile as sf, numpy as np
d = r'C:\00_Data\RGM\NoteGT\_sources\loopdetect-run'
for tag, path in [('user', d + r'\user-bank\icechime.ogg'),
                  ('vanilla', d + r'\vanilla-12111\icechime.ogg')]:
    x, sr = sf.read(path, dtype='float64', always_2d=True)
    x = x[:, 0] if x.ndim == 2 else x
    peak = float(np.abs(x).max())
    clip = int(np.sum(np.abs(x) > 0.999))
    clip90 = int(np.sum(np.abs(x) > 0.90))
    # 削波形态：相邻样本都饱和（平顶）
    sat = np.abs(x) > 0.995
    plateau = int(np.sum(sat[1:] & sat[:-1]))
    # 噪声底：最后 10% 的 RMS（dBFS 相对 peak）
    tail = x[int(len(x) * 0.9):]
    nfl = float(np.sqrt(np.mean(tail ** 2)))
    # DC
    dc = float(np.abs(x.mean()))
    dur = len(x) / sr
    print(f'{tag:8s} sr={sr} dur={dur:.3f}s peak={peak:.4f} '
          f'|>0.999|={clip} (>0.90|={clip90}) 平顶段={plateau} '
          f'尾部RMS={20*np.log10(max(nfl,1e-12)/max(peak,1e-12)):.1f}dBFS DC={dc:.2e}')
    # 前 200 样本看 pre-skip 是否裁头（首样本幅值）
    print(f'          head={float(np.abs(x[:8]).max()):.4f} 首8样本={np.round(x[:8],4).tolist()}')
