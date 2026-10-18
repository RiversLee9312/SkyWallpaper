"""Generate offline notices from the actual release and debug runtime dependencies."""
from collections import defaultdict
from io import BytesIO
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.error import HTTPError
from urllib.request import urlopen
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/licenses'
REPORT = ROOT / 'app/build/reports/licenses/dependencies.json'
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
GROUPS = {
    'androidx': ('AndroidX', 'The Android Open Source Project', 'https://android.googlesource.com/platform/frameworks/support/'),
    'kotlin': ('Kotlin standard library', 'JetBrains s.r.o. and contributors', 'https://github.com/JetBrains/kotlin'),
    'coroutines': ('kotlinx.coroutines', 'JetBrains s.r.o. and Kotlin contributors', 'https://github.com/Kotlin/kotlinx.coroutines'),
    'serialization': ('kotlinx.serialization', 'JetBrains s.r.o. and Kotlin contributors', 'https://github.com/Kotlin/kotlinx.serialization'),
    'annotations': ('JetBrains annotations', 'JetBrains s.r.o. and contributors', 'https://github.com/JetBrains/java-annotations'),
    'jspecify': ('JSpecify', 'The JSpecify Authors', 'https://github.com/jspecify/jspecify'),
    'guava': ('Guava ListenableFuture', 'The Guava Authors', 'https://github.com/google/guava'),
    'material-symbols': ('Material Symbols Rounded', 'Google LLC', 'https://github.com/google/material-design-icons'),
}


def read_pom(coordinate):
    group, name, version = coordinate.split(':')
    cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches/modules-2/files-2.1' / group / name / version
    cached = next(cache.glob('*/*.pom'), None)
    if cached:
        return ET.parse(cached).getroot()
    relative = f'{group.replace(".", "/")}/{name}/{version}/{name}-{version}.pom'
    for repository in ('https://repo.maven.apache.org/maven2/', 'https://dl.google.com/dl/android/maven2/'):
        try:
            return ET.fromstring(urlopen(repository + relative, timeout=30).read())
        except HTTPError as error:
            if error.code != 404:
                raise
    raise RuntimeError(f'Missing parent POM: {coordinate}')


def declared_licenses(pom, visited=None):
    licenses = [(node.findtext('m:name', '', NS), node.findtext('m:url', '', NS))
                for node in pom.findall('m:licenses/m:license', NS)]
    if licenses:
        return licenses
    parent = pom.find('m:parent', NS)
    if parent is None:
        raise RuntimeError('No license declared in a resolved POM or its parents')
    coordinate = ':'.join(parent.findtext('m:' + key, '', NS) for key in ('groupId', 'artifactId', 'version'))
    visited = set() if visited is None else visited
    if coordinate in visited:
        raise RuntimeError('Cyclic parent POM')
    visited.add(coordinate)
    return declared_licenses(read_pom(coordinate), visited)


def family(coordinate):
    group, module, _ = coordinate.split(':')
    if group.startswith('androidx.'):
        return 'androidx'
    if group == 'org.jetbrains.kotlin':
        return 'kotlin'
    if group == 'org.jetbrains.kotlinx' and module.startswith('kotlinx-coroutines'):
        return 'coroutines'
    if group == 'org.jetbrains.kotlinx' and module.startswith('kotlinx-serialization'):
        return 'serialization'
    return {'org.jetbrains': 'annotations', 'org.jspecify': 'jspecify', 'com.google.guava': 'guava'}[group]


def embedded_notices(archive, prefix=''):
    for name in archive.namelist():
        if re.search(r'(?:^|/)(?:LICENSE|NOTICE|COPYRIGHT)(?:[._-]|$)', name, re.IGNORECASE) and not name.endswith('/'):
            yield prefix + name, archive.read(name).decode('utf-8-sig').replace('\r\n', '\n').strip()
    if 'classes.jar' in archive.namelist():
        with ZipFile(BytesIO(archive.read('classes.jar'))) as nested:
            yield from embedded_notices(nested, prefix + 'classes.jar!/')


def main():
    gradle = ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    subprocess.run([str(gradle), ':app:exportLicenseDependencies', '--no-configuration-cache', '--console=plain'], cwd=ROOT, check=True)
    rows = json.loads(REPORT.read_text(encoding='utf-8'))
    grouped = defaultdict(list)
    preserved = defaultdict(dict)
    canonical = (ROOT / 'LICENSE').read_text(encoding='utf-8').strip()
    for row in rows:
        licenses = declared_licenses(ET.parse(row['pom']).getroot())
        # Require an explicit review when a newly introduced license is not covered here.
        if any('apache.org/licenses/LICENSE-2.0' not in url for _, url in licenses):
            raise RuntimeError(f'Review the new license for {row["coordinate"]}: {licenses}')
        key = family(row['coordinate'])
        grouped[key].append(row)
        for path in row['artifacts']:
            with ZipFile(path) as archive:
                for name, text in embedded_notices(archive):
                    normalized = re.sub(r'\s+', '', text)
                    if normalized != re.sub(r'\s+', '', canonical):
                        preserved[key].setdefault(normalized, (f'{row["coordinate"]} / {name}', text))
    grouped['material-symbols'] = []
    symbol_license = (ROOT / 'artwork/material-symbols-LICENSE.txt').read_text(encoding='utf-8').strip()
    if re.sub(r'\s+', '', symbol_license) != re.sub(r'\s+', '', canonical):
        preserved['material-symbols']['original'] = ('Upstream Material Symbols license', symbol_license)
    ASSETS.mkdir(parents=True, exist_ok=True)
    project = (ROOT / 'NOTICE').read_text(encoding='utf-8').rstrip() + '\n\n' + canonical + '\n'
    (ASSETS / 'project.txt').write_text(project, encoding='utf-8', newline='\n')
    catalog = []
    document = [
        '# Third-party notices',
        '',
        'Generated by `python tools/update_licenses.py` from the resolved release and debug runtime artifacts, including transitive dependencies. Test-only dependencies and downloaded build tools are not part of the application.',
        '',
        'Each component retains its own copyright and license. Complete license texts and embedded upstream notices are bundled offline in the application under **Settings → Open source licenses**.',
        '',
        'The dependency catalog is checked against both runtime configurations before every build. Debug-only modules are marked below.',
    ]
    for key in sorted(grouped, key=lambda value: GROUPS[value][0].lower()):
        name, holder, url = GROUPS[key]
        entries = grouped[key]
        coordinates = [entry['coordinate'] for entry in entries]
        asset = f'licenses/{key}.txt'
        body = [name, f'Copyright {holder}', url, 'License: Apache License 2.0']
        if coordinates:
            body += ['Maven coordinates:\n' + '\n'.join(coordinates)]
        if key == 'material-symbols':
            body += ['The original SVGs were converted to Android VectorDrawable format. Viewport offsets were translated; path geometry was preserved.']
        body += [canonical]
        for source, text in preserved[key].values():
            body += [f'Additional upstream notice ({source}):\n\n{text}']
        (ROOT / 'app/src/main/assets' / asset).write_text('\n\n'.join(body) + '\n', encoding='utf-8', newline='\n')
        catalog.append({'id': key, 'name': name, 'license': 'Apache License 2.0', 'asset': asset,
                        'coordinates': coordinates, 'scopes': {entry['coordinate']: entry['scopes'] for entry in entries}})
        document += ['', f'## {name}', '', f'Copyright {holder}. [Upstream project]({url}). Apache-2.0.', '', f'[Full license and notices](app/src/main/assets/{asset}).']
        if coordinates:
            document += ['', *[f'- `{entry["coordinate"]}`' + (' — debug only' if entry['scopes'] == ['debug'] else '') for entry in entries]]
        else:
            document += ['', 'Rounded UI symbols converted from SVG to Android VectorDrawable. Paths are preserved and viewport offsets translated. The upstream license is also retained in `artwork/material-symbols-LICENSE.txt`.']
    (ASSETS / 'catalog.json').write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    wrapper_dir = ROOT / 'licenses'
    wrapper_dir.mkdir(exist_ok=True)
    with ZipFile(ROOT / 'gradle/wrapper/gradle-wrapper.jar') as archive:
        (wrapper_dir / 'gradle-wrapper-LICENSE.txt').write_bytes(archive.read('META-INF/LICENSE'))
    document += ['', '## Gradle wrapper', '',
                 'The repository redistributes the Gradle wrapper JAR and scripts under Apache-2.0. The JAR’s original [embedded license](licenses/gradle-wrapper-LICENSE.txt) is preserved. Gradle itself is downloaded during builds and is not included in the APK.', '',
                 'JUnit, Espresso and other test tooling are resolved separately for tests; they are not shipped in the main application.', '']
    (ROOT / 'THIRD_PARTY_NOTICES.md').write_text('\n'.join(document), encoding='utf-8', newline='\n')
    print(f'Generated {len(catalog)} library entries covering {len(rows)} resolved modules and Material Symbols.')


if __name__ == '__main__':
    main()
