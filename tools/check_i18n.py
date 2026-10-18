from pathlib import Path
from collections import Counter
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
ANDROID = "{http://schemas.android.com/apk/res/android}"
LOCALIZED = {
    "en": "values-en",
    "zh-Hans": "values-b+zh+Hans",
    "zh-Hant": "values-b+zh+Hant",
    "es": "values-es",
    "fr": "values-fr",
    "de": "values-de",
    "it": "values-it",
    "pt-BR": "values-pt-rBR",
    "ja": "values-ja",
    "ko": "values-ko",
    "ru": "values-ru",
    "ar": "values-ar",
    "hi": "values-hi",
    # Android's resource matcher uses the legacy Indonesian code, while LocaleConfig uses id.
    "id": "values-in",
    "tr": "values-tr",
    "nl": "values-nl",
    "pl": "values-pl",
    "uk": "values-uk",
    "vi": "values-vi",
    "th": "values-th",
    "bn": "values-bn",
}
PLACEHOLDER = re.compile(r"%(?:(\d+)\$)?([a-zA-Z])")
LOADING_KEYS = ("gps_fallback", "gps_loading", "coarse_loading")

def load_strings(path):
    root = ET.parse(path).getroot()
    entries = root.findall("string")
    names = [entry.attrib.get("name") for entry in entries]
    if len(names) != len(set(names)):
        raise AssertionError(f"{path}: duplicate string key")
    return {entry.attrib["name"]: entry.text or "" for entry in entries}

def placeholders(value):
    return Counter(PLACEHOLDER.findall(value))

def escaped_android_quote(text, char):
    for i, value in enumerate(text):
        if value == char:
            slash_count = 0
            j = i - 1
            while j >= 0 and text[j] == "\\":
                slash_count += 1
                j -= 1
            if slash_count % 2 == 0:
                return False
    return True

base = load_strings(ROOT / "values" / "strings.xml")
base_keys = set(base)
for locale, folder in LOCALIZED.items():
    path = ROOT / folder / "strings.xml"
    strings = load_strings(path)
    if set(strings) != base_keys:
        missing = sorted(base_keys - set(strings))
        extra = sorted(set(strings) - base_keys)
        raise AssertionError(f"{locale}: key mismatch; missing={missing}, extra={extra}")
    for key, source in base.items():
        value = strings[key]
        if placeholders(value) != placeholders(source):
            raise AssertionError(f"{locale}/{key}: format placeholders differ: {placeholders(value)} != {placeholders(source)}")
        if value.count(r"\n") != source.count(r"\n"):
            raise AssertionError(f"{locale}/{key}: Android escaped newline count differs")
        if not escaped_android_quote(value, "'") or not escaped_android_quote(value, '"'):
            raise AssertionError(f"{locale}/{key}: unescaped ASCII quote or apostrophe")
    if strings["app_name"] != "Sky Wallpaper":
        raise AssertionError(f"{locale}: app brand changed")
    if "Sky Wallpaper" not in strings["wallpaper_unavailable"]:
        raise AssertionError(f"{locale}: wallpaper error is missing the app brand")
    if strings["utc_label"] != "UTC / GMT":
        raise AssertionError(f"{locale}: UTC / GMT marker changed")
    if strings["percent_format"] != "%1$d%%":
        raise AssertionError(f"{locale}: percentage format changed")
    if "°" not in strings["solar_elevation"]:
        raise AssertionError(f"{locale}: required degree marker is missing")
    for key in LOADING_KEYS:
        if not strings[key].endswith("…"):
            raise AssertionError(f"{locale}/{key}: must end with Unicode ellipsis")
    print(f"PASS {locale}: {len(strings)} strings")

config = ET.parse(ROOT / "xml" / "locales_config.xml").getroot()
configured = [entry.attrib.get(ANDROID + "name") for entry in config.findall("locale")]
expected = list(LOCALIZED)
if configured != expected:
    raise AssertionError(f"locale config mismatch: {configured}")
print(f"PASS locale config: {len(configured)} locales")
languages = {locale.split("-")[0] for locale in configured}
if len(languages) < 20:
    raise AssertionError(f"Expected at least 20 distinct languages; found {len(languages)}")
print(f"PASS coverage: {len(languages)} distinct languages")
print(f"PASS resources: {len(base_keys)} keys in every localized file")
