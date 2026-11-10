#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""剥离 index.html（HTML/CSS/JS）与 Java 源码中的注释。
策略：
- HTML 注释 <!-- --> 全删
- <style>/* */ 与 <script>/* */ 全删
- <script> 内 // 行注释全删（状态机识别字符串/模板串/正则，保护 https:// 与除法）
- Java：状态机删 // 与 /* */（识别字符串/字符字面量）
"""
import io
import re
import sys


def strip_java(src):
    out = []
    i, n = 0, len(src)
    state = 'code'  # code | sq | dq | line | block
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ''
        if state == 'code':
            if c == '/' and nxt == '/':
                state = 'line'
                i += 2
                continue
            if c == '/' and nxt == '*':
                state = 'block'
                i += 2
                continue
            if c == '"':
                state = 'dq'
            elif c == "'":
                state = 'sq'
            out.append(c)
            i += 1
            continue
        if state == 'line':
            if c == '\n':
                state = 'code'
                out.append(c)
            i += 1
            continue
        if state == 'block':
            if c == '*' and nxt == '/':
                state = 'code'
                i += 2
                continue
            if c == '\n':
                out.append(c)  # 保留换行，避免行号塌陷
            i += 1
            continue
        # sq/dq
        if c == '\\':
            out.append(c)
            if i + 1 < n:
                out.append(src[i + 1])
            i += 2
            continue
        if (state == 'dq' and c == '"') or (state == 'sq' and c == "'"):
            state = 'code'
        out.append(c)
        i += 1
    return ''.join(out)


REGEX_PREV = set('(,=:[!&|?{};+-*%<>~^')
REGEX_KW = ('return', 'typeof', 'case', 'in', 'of', 'new', 'delete',
            'void', 'instanceof', 'do', 'else', 'yield', 'await')


def strip_js(src):
    """JS 注释剥离，识别单/双引号、模板串、正则字面量。"""
    out = []
    i, n = 0, len(src)
    state = 'code'
    prev_sig = ''      # 最近一个非空白代码字符
    prev_word = ''     # 最近一个标识符（用于 return / 正则判断）
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ''
        if state == 'code':
            if c == '/' and nxt == '/':
                state = 'line'
                i += 2
                continue
            if c == '/' and nxt == '*':
                state = 'block'
                i += 2
                continue
            if c == '/' and (prev_sig in REGEX_PREV or prev_word in REGEX_KW or prev_sig == ''):
                # 正则字面量：原样拷贝到收尾 / 标志位
                out.append(c)
                i += 1
                in_cls = False
                while i < n:
                    ch = src[i]
                    if ch == '\\' and i + 1 < n:
                        out.append(ch)
                        out.append(src[i + 1])
                        i += 2
                        continue
                    if ch == '[':
                        in_cls = True
                    elif ch == ']':
                        in_cls = False
                    elif ch == '/' and not in_cls:
                        out.append(ch)
                        i += 1
                        break
                    elif ch == '\n':
                        break
                    out.append(ch)
                    i += 1
                while i < n and (src[i].isalpha()):
                    out.append(src[i])
                    i += 1
                prev_sig = '/'
                prev_word = ''
                continue
            if c == '"':
                state = 'dq'
            elif c == "'":
                state = 'sq'
            elif c == '`':
                state = 'tpl'
            if not c.isspace():
                if c.isalnum() or c in '_$':
                    prev_word = (prev_word + c) if prev_word else c
                else:
                    prev_word = ''
                prev_sig = c
            out.append(c)
            i += 1
            continue
        if state == 'line':
            if c == '\n':
                state = 'code'
                out.append(c)
            i += 1
            continue
        if state == 'block':
            if c == '*' and nxt == '/':
                state = 'code'
                i += 2
                continue
            if c == '\n':
                out.append(c)
            i += 1
            continue
        # 字符串/模板
        if c == '\\':
            out.append(c)
            if i + 1 < n:
                out.append(src[i + 1])
            i += 2
            continue
        if state == 'tpl':
            if c == '`':
                state = 'code'
            out.append(c)
            i += 1
            continue
        if (state == 'dq' and c == '"') or (state == 'sq' and c == "'"):
            state = 'code'
        out.append(c)
        i += 1
    return ''.join(out)


def strip_html_markup(src):
    """非 script 区域的 <!-- --> 与 /* */ 剥离。"""
    src = re.sub(r'<!--.*?-->', '', src, flags=re.S)
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    return src


def process_html(path):
    src = io.open(path, encoding='utf-8').read()
    parts = re.split(r'(<script\b[^>]*>|</script>)', src, flags=re.I)
    out = []
    in_script = False
    for p in parts:
        if re.match(r'<script\b', p, re.I):
            in_script = True
            out.append(p)
        elif re.match(r'</script>', p, re.I):
            in_script = False
            out.append(p)
        else:
            out.append(strip_js(p) if in_script else strip_html_markup(p))
    io.open(path, 'w', encoding='utf-8', newline='').write(''.join(out))


def process_java(path):
    src = io.open(path, encoding='utf-8').read()
    io.open(path, 'w', encoding='utf-8', newline='').write(strip_java(src))


if __name__ == '__main__':
    target = sys.argv[1]
    if target.endswith('.html'):
        process_html(target)
    else:
        process_java(target)
    print('stripped', target)
