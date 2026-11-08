import struct, zipfile, os
src = 'build/base.apk'
out = 'build/aligned.apk'
items = []
with zipfile.ZipFile(src) as z:
    for info in z.infolist():
        items.append((info.filename, z.read(info.filename)))
items.append(('classes.dex', open('build/dex/classes.dex', 'rb').read()))
rank = {'AndroidManifest.xml': 0, 'classes.dex': 1, 'resources.arsc': 2}
items.sort(key=lambda e: rank.get(e[0], 9))
with zipfile.ZipFile(out, 'w') as z:
    for name, data in items:
        zi = zipfile.ZipInfo(name)
        zi.compress_type = zipfile.ZIP_STORED if name == 'resources.arsc' else zipfile.ZIP_DEFLATED
        zi.date_time = (2026, 1, 1, 0, 0, 0)
        if name == 'resources.arsc':
            cur = z.fp.tell()
            base = 30 + len(name.encode('utf-8')) + 4
            pad = (-(cur + base)) % 4
            zi.extra = struct.pack('<HH', 0xD935, pad) + b'\x00' * pad
        z.writestr(zi, data)
print('wrote', out, os.path.getsize(out))
