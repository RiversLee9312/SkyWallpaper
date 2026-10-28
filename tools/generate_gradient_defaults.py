"""Generate context-free Kotlin defaults from the exported gradient profiles."""
from pathlib import Path
import json
import math
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/assets/default_gradients.json'
TARGET = ROOT / 'app/src/main/java/com/example/skywallpaper/GradientDefaults.kt'


def number(value):
    assert math.isfinite(value)
    text = format(value, '.9g')
    assert struct.pack('f', float(text)) == struct.pack('f', value)
    return text + 'f'


def generate():
    profiles = json.loads(SOURCE.read_text(encoding='utf-8'))
    types = ['LINEAR', 'RADIAL', 'SWEEP']
    phases = ['DAWN', 'DAY', 'DUSK', 'NIGHT']
    assert set(profiles) == set(types)
    lines = ['package com.example.skywallpaper', '',
             '// Generated from assets/default_gradients.json, exported from the device on 2026-10-28.',
             '// Regenerate with python tools/generate_gradient_defaults.py.',
             'internal object GradientDefaults {',
             '    private val profiles = mapOf(']
    for kind in types:
        assert set(profiles[kind]) == set(phases)
        lines.append(f'        GradientType.{kind} to mapOf(')
        for phase in phases:
            gradient = profiles[kind][phase]
            stops = gradient['stops']
            assert 2 <= len(stops) <= 6
            assert all(0 <= s['position'] <= 1 for s in stops)
            assert all(b['position'] - a['position'] >= .009 for a, b in zip(stops, stops[1:]))
            assert .1 <= gradient['radius'] <= 4
            lines += [f'            SkyPhase.{phase} to SkyGradient(', '                stops = listOf(']
            lines += [f'                    GradientStop(0x{s["color"] & 0xffffffff:08X}.toInt(), {number(s["position"])}),' for s in stops]
            lines += ['                ),']
            for name in ['start', 'end', 'center']:
                x, y = gradient[name]
                assert 0 <= x <= 1 and 0 <= y <= 1
                lines += [f'                {name} = GradientPoint({number(x)}, {number(y)}),']
            lines += [f'                radius = {number(gradient["radius"])},', '            ),']
        lines += ['        ),']
    lines += ['    )', '', '    fun get(phase: SkyPhase, type: GradientType): SkyGradient = profiles.getValue(type).getValue(phase)', '}', '']
    return '\n'.join(lines)


if __name__ == '__main__':
    result = generate()
    if '--check' in sys.argv:
        assert TARGET.read_text(encoding='utf-8') == result, 'Run tools/generate_gradient_defaults.py to update Kotlin defaults'
        print('Verified all 12 bundled gradient profiles')
    else:
        TARGET.write_text(result, encoding='utf-8')
        print('Generated all 12 bundled gradient profiles')
