"""Small adb helper for inspecting and tapping the real OnePlus UI."""
from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / 'verification'
ROOT.mkdir(exist_ok=True)
SDK = Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
           or str(Path(os.environ.get('LOCALAPPDATA', '')) / 'Android/Sdk'))
ADB = shutil.which('adb') or str(SDK / 'platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb'))
def adb(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True, encoding='utf-8', check=True).stdout
def nodes():
    adb('shell','uiautomator','dump','/data/local/tmp/sky-ui-live.xml')
    adb('pull','/data/local/tmp/sky-ui-live.xml',str(ROOT/'ui-live.xml'))
    return ET.parse(ROOT/'ui-live.xml').getroot().iter('node')
action = sys.argv[1]
if action == 'tap':
    target = sys.argv[2]
    snapshot = list(nodes())
    parents = {child: parent for parent in snapshot for child in parent}
    matches = [n for n in snapshot if n.get('text') == target or n.get('content-desc') == target]
    clickable = []
    for match in matches:
        node = match
        while node is not None and node.get('clickable') != 'true':
            node = parents.get(node)
        if node is not None and node not in clickable:
            clickable.append(node)
    if clickable:
        matches = clickable
    if len(matches) != 1:
        raise RuntimeError(f'Expected one match for {target!r}, got {len(matches)}')
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',matches[0].get('bounds')))
    print(adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)))
elif action == 'capture':
    name = sys.argv[2]
    list(nodes())
    adb('shell','screencap','-p','/data/local/tmp/sky-ui-live.png')
    adb('pull','/data/local/tmp/sky-ui-live.png',str(ROOT/f'{name}.png'))
    (ROOT/f'{name}.xml').write_bytes((ROOT/'ui-live.xml').read_bytes())
    print(ROOT/f'{name}.png')
elif action == 'read':
    print(json.dumps([{k:n.get(k) for k in ['text','content-desc','bounds','clickable','enabled']}
                      for n in nodes() if n.get('text') or n.get('content-desc')],ensure_ascii=False,indent=2))
else:
    raise RuntimeError('Use tap, capture, or read')
