#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""构建时把 secrets.json 里的密钥注入到源文件，生成可打包的临时副本。

用法:
    python scripts/inject_secrets.py          # 注入到 build/ 下的副本
    python scripts/inject_secrets.py --check  # 只检查占位符是否齐全

仓库中的源文件只有占位符 __SIGN_KEY_CHATGLM__ / __SIGN_KEY_ZHIPU__，
真实值放在本地 secrets.json（已被 .gitignore 排除）。
"""
import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SECRETS = os.path.join(ROOT, 'secrets.json')

PLACEHOLDERS = {
    '__SIGN_KEY_CHATGLM__': 'chatglm_sign_key',
    '__SIGN_KEY_ZHIPU__': 'zhipu_sign_key',
}

# 每个文件只校验它实际含有的占位符
TARGETS = [
    os.path.join(ROOT, 'inject', 'com', 'nbasdws', 'zjsj', 'CustomRoute.java'),
    os.path.join(ROOT, 'assets', 'index.html'),
]


def load_secrets():
    if not os.path.isfile(SECRETS):
        return None
    with io.open(SECRETS, encoding='utf-8') as fh:
        return json.load(fh)


def check_only():
    missing = []
    found = 0
    for path in TARGETS:
        if not os.path.isfile(path):
            missing.append((path, 'file not found'))
            continue
        with io.open(path, encoding='utf-8') as fh:
            src = fh.read()
        for ph in PLACEHOLDERS:
            if ph in src:
                found += 1
    if missing:
        for path, why in missing:
            print('WARN %s: %s' % (path, why))
        return 1
    if not found:
        print('ERROR: no placeholder found in any target')
        return 1
    print('OK: %d placeholder occurrence(s) present' % found)
    return 0


def main():
    if '--check' in sys.argv:
        return check_only()

    secrets = load_secrets()
    if secrets is None:
        print('ERROR: secrets.json not found at %s' % SECRETS)
        print('Create it with:')
        print(json.dumps({
            'chatglm_sign_key': '<your key>',
            'zhipu_sign_key': '<your key>',
        }, indent=2))
        return 1

    outdir = os.path.join(ROOT, 'build')
    if not os.path.isdir(outdir):
        os.makedirs(outdir)

    for path in TARGETS:
        if not os.path.isfile(path):
            print('SKIP %s (not found)' % path)
            continue
        with io.open(path, encoding='utf-8') as fh:
            src = fh.read()
        for ph, key in PLACEHOLDERS.items():
            val = secrets.get(key, '')
            if not val:
                print('ERROR: secrets.json missing "%s"' % key)
                return 1
            src = src.replace(ph, val)
        rel = os.path.relpath(path, ROOT).replace(os.sep, '__')
        dst = os.path.join(outdir, rel)
        with io.open(dst, 'w', encoding='utf-8', newline='') as fh:
            fh.write(src)
        print('wrote %s' % dst)
    return 0


if __name__ == '__main__':
    sys.exit(main())
