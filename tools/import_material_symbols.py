"""Import Google's Rounded Material Symbols as bundled Android vector assets."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import sys
from urllib.request import urlopen
import xml.etree.ElementTree as ET
from xml.sax.saxutils import quoteattr

ROOT = Path(__file__).resolve().parents[1]
SYMBOLS = ['settings', 'wallpaper', 'routine', 'location_on', 'my_location',
           'edit_location_alt', 'share_location', 'admin_panel_settings', 'language',
           'tune', 'shield', 'wb_twilight', 'dark_mode', 'history', 'gradient', 'photo_library', 'add', 'delete', 'description', 'code',
           'visibility', 'fullscreen', 'fullscreen_exit', 'folder_zip', 'file_export', 'file_open']
BASE = 'https://raw.githubusercontent.com/google/material-design-icons/master/'

def save(name):
    url = f'{BASE}symbols/web/{name}/materialsymbolsrounded/{name}_24px.svg'
    try:
        svg = urlopen(url, timeout=30).read()
    except Exception as error:
        raise RuntimeError(f'Could not import {name}: {error}') from error
    node = ET.fromstring(svg)
    x, y, width, height = [float(v) for v in node.attrib['viewBox'].split()]
    paths = '\n'.join(f'        <path android:fillColor="#FF000000" android:pathData={quoteattr(p.attrib["d"])} />'
                      for p in node.findall('{http://www.w3.org/2000/svg}path'))
    source = (f'<!-- Google Material Symbols Rounded, Apache-2.0. Source: {url}\n'
              '     Modified: converted SVG to Android VectorDrawable with viewport translation. -->\n'
              f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="{width:g}" android:viewportHeight="{height:g}">\n'
              f'    <group android:translateX="{-x:g}" android:translateY="{-y:g}">\n{paths}\n    </group>\n</vector>\n')
    (ROOT / f'app/src/main/res/drawable/ic_symbol_{name}.xml').write_text(source, encoding='utf-8')
    return name

if __name__ == '__main__':
    names = sys.argv[1:] or SYMBOLS
    if any(name not in SYMBOLS for name in names):
        raise ValueError('Unknown Material Symbol')
    with ThreadPoolExecutor(max_workers=5) as executor:
        print('Imported:', ', '.join(executor.map(save, names)))
    license_path = ROOT / 'artwork/material-symbols-LICENSE.txt'
    license_path.write_bytes(urlopen(BASE + 'LICENSE', timeout=30).read())
