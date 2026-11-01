"""Print APK size evidence without executing or extracting its contents."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def measure(path):
    path = Path(path)
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(65536), b''):
            digest.update(chunk)
    with zipfile.ZipFile(path) as archive:
        libraries = [
            {'path': entry.filename, 'compressed_bytes': entry.compress_size,
             'uncompressed_bytes': entry.file_size}
            for entry in archive.infolist()
            if entry.filename.startswith('lib/') and entry.filename.endswith('.so')
        ]
    return {'apk': path.name, 'apk_bytes': path.stat().st_size,
            'sha256': digest.hexdigest(), 'native_libraries': libraries}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk')
    print(json.dumps(measure(parser.parse_args().apk), indent=2))
