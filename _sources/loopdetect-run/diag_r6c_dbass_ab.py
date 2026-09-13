# -*- coding: utf-8 -*-
"""dbass(用户) 12s 循环试听渲染 —— 供用户判断 2.1Hz/6-7dB 起伏（ghost pluck）可闻性"""
import os, sys
import numpy as np
import soundfile as sf
import importlib
D = r'C:\00_Data\RGM\NoteGT\_sources\loopdetect-run'
sys.path.insert(0, D)
import loopdetect as ld
importlib.reload(ld)

x, sr = sf.read(os.path.join(D, 'user-bank', 'dbass.ogg'), dtype='float64')
ld.SR = int(sr)
x = ld.preprocess(x)
b, why = ld.find_loop(x)
a, L, k, f0 = b['a'], b['L'], b['k'], b['f0']
loop = ld.build_loop(x, a, L, k, f0, xfade=0.005)
print(f'dbass: a={a/sr*1000:.1f}ms L={L/sr*1000:.1f}ms k={k} f0={f0:.2f}Hz')

os.makedirs(os.path.join(D, 'ab', 'dbass'), exist_ok=True)
y = ld.render(x, a, loop, total=12.0)
sf.write(os.path.join(D, 'ab', 'dbass', 'dbs-A.wav'),
         (y/np.abs(y).max()*0.9).astype(np.float32), sr)
print('rendered ab/dbass/dbs-A.wav (v5 as-is, 12s)')
