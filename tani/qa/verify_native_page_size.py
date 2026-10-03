#!/usr/bin/env python3
"""Verify 64-bit ELF LOAD and GNU_RELRO alignment inside APK/AAB/AAR archives."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('archives', nargs='+', type=Path)
parser.add_argument('--output', type=Path)
args = parser.parse_args()
results = []
failed = False
for archive in args.archives:
    with zipfile.ZipFile(archive) as package:
        for name in package.namelist():
            if not name.endswith('.so'):
                continue
            data = package.read(name)
            if data[:4] != b'\x7fELF':
                raise ValueError(f'{archive.name}:{name}: invalid ELF')
            if data[4] != 2:
                continue  # The Android 16 KB page-size targets use 64-bit binaries.
            endian = '<' if data[5] == 1 else '>'
            offset = struct.unpack_from(endian + 'Q', data, 32)[0]
            size, count = struct.unpack_from(endian + 'HH', data, 54)
            loads, relro = [], []
            for index in range(count):
                segment = struct.unpack_from(endian + 'IIQQQQQQ', data, offset + index * size)
                kind, flags, file_offset, address, _, file_size, memory_size, alignment = segment
                if kind == 1:
                    loads.append({'alignment': alignment, 'congruent': (address - file_offset) % 16384 == 0})
                if kind == 0x6474E552:
                    relro.append({'end_mod_16kb': (address + memory_size) % 16384})
            passed = bool(loads) and all(x['alignment'] >= 16384 and x['congruent'] for x in loads) and all(x['end_mod_16kb'] == 0 for x in relro)
            result = {'archive': str(archive), 'library': name, 'sha256': hashlib.sha256(data).hexdigest(),
                      'load_segments': loads, 'relro_segments': relro, 'passed': passed}
            results.append(result)
            failed |= not passed
            print(('PASS' if passed else 'FAIL') + f': {archive.name}:{name} 16 KB LOAD/RELRO')
if args.output:
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({'passed': not failed, 'libraries': results}, indent=2) + '\n')
if failed:
    sys.exit(1)
print(f'PASS: checked {len(results)} 64-bit native libraries; runtime device testing remains required')
