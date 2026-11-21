"""Writes the store texts as files and builds one upload folder per store and language.

Source of the texts: docs/store-listing.md (one section per language). Checks the length limits
of App Store Connect and Google Play and fails if a text is too long.

Usage: python3 tools/store/store_metadata.py
"""
import os, re, shutil, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DOC = os.path.join(ROOT, 'docs/store-listing.md')
STORE = os.path.join(ROOT, 'assets/store')
BRAND = os.path.join(ROOT, 'assets/brand')

# Section heading in the doc -> (asset folder, App Store locale, Play locale)
LANGS = {'English': ('en', 'en-US', 'en-US'), 'Deutsch': ('de', 'de-DE', 'de-DE'),
         'Français': ('fr', 'fr-FR', 'fr-FR'), 'Español': ('es', 'es-ES', 'es-ES')}
FIELDS = ['name', 'subtitle', 'short_description', 'promotional_text', 'release_notes', 'keywords']
# Order of the bullets in each language section
ORDER = ['name', 'subtitle', 'short_description', 'promotional_text', 'release_notes', 'keywords']
LIMITS = {'name': 30, 'subtitle': 30, 'short_description': 80, 'promotional_text': 170,
          'keywords': 100, 'release_notes': 500, 'description': 4000}


def parse():
    text = open(DOC, encoding='utf-8').read()
    out = {}
    for heading in LANGS:
        m = re.search(r'^### %s\n(.*?)(?=^### |\Z)' % re.escape(heading), text, re.S | re.M)
        if not m:
            sys.exit('No section for %s in %s' % (heading, DOC))
        body = m.group(1)
        bullets = re.findall(r'^- \*\*[^*]+\*\*[  ]?(.*)$', body, re.M)
        # The last bullet is the description label; its text follows as paragraphs
        values = [b.strip() for b in bullets[:len(ORDER)]]
        if len(values) < len(ORDER):
            sys.exit('%s: expected %d fields, found %d' % (heading, len(ORDER), len(values)))
        fields = dict(zip(ORDER, values))
        after = body[body.index(bullets[len(ORDER) - 1]) + len(bullets[len(ORDER) - 1]):]
        desc = after.split('\n', 2)[2] if after.count('\n') >= 2 else ''
        fields['description'] = desc.strip() + '\n'
        out[heading] = fields
    return out


def write(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(value if value.endswith('\n') else value + '\n')


def copy_all(src, dst):
    os.makedirs(dst, exist_ok=True)
    for name in sorted(os.listdir(src)):
        shutil.copy2(os.path.join(src, name), os.path.join(dst, name))


def main():
    data = parse()
    errors = []
    for heading, fields in data.items():
        for key, limit in LIMITS.items():
            n = len(fields[key].strip())
            if n > limit:
                errors.append('%s %s: %d characters, limit %d' % (heading, key, n, limit))
    if errors:
        sys.exit('Too long:\n  ' + '\n  '.join(errors))

    for sub in ('metadata', 'upload'):
        shutil.rmtree(os.path.join(STORE, sub), ignore_errors=True)
    for heading, fields in data.items():
        lang, ios, play = LANGS[heading]
        # App Store Connect fields
        a = os.path.join(STORE, 'metadata/appstore', ios)
        for key in ('name', 'subtitle', 'promotional_text', 'keywords', 'release_notes', 'description'):
            write(os.path.join(a, key + '.txt'), fields[key])
        # Google Play fields
        p = os.path.join(STORE, 'metadata/play', play)
        write(os.path.join(p, 'title.txt'), fields['name'])
        write(os.path.join(p, 'short_description.txt'), fields['short_description'])
        write(os.path.join(p, 'full_description.txt'), fields['description'])
        write(os.path.join(p, 'release_notes.txt'), fields['release_notes'])
        # Everything for one store and language in one folder
        ua = os.path.join(STORE, 'upload/appstore', ios)
        copy_all(a, ua)
        copy_all(os.path.join(STORE, 'screenshots/iphone-6.9', lang), os.path.join(ua, 'screenshots-iphone-6.9'))
        up = os.path.join(STORE, 'upload/play', play)
        copy_all(p, up)
        copy_all(os.path.join(STORE, 'screenshots/android-phone', lang), os.path.join(up, 'screenshots-phone'))
        shutil.copy2(os.path.join(STORE, 'feature', 'feature-graphic-1024x500-%s.png' % lang), os.path.join(up, 'feature-graphic.png'))
        shutil.copy2(os.path.join(BRAND, 'play-icon-512.png'), os.path.join(up, 'icon-512.png'))
    shutil.copy2(os.path.join(BRAND, 'app-icon-1024.png'), os.path.join(STORE, 'upload/appstore/app-icon-1024.png'))
    for heading, fields in data.items():
        print('%-9s' % heading, ', '.join('%s %d/%d' % (k, len(fields[k].strip()), LIMITS[k]) for k in LIMITS))


if __name__ == '__main__':
    main()
