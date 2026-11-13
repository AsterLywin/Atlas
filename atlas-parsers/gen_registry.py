#!/usr/bin/env python3
"""Regenerate the Kotatsu parser registry for the vendored parsers tree.

Upstream (Kotatsu-Redo/kotatsu-parsers-redo) generates these files at compile
time with a KSP processor (kotatsu-parsers-ksp/ParserProcessor.kt). Atlas
vendors the parsers as plain sources and does NOT run KSP, so this script
replicates the processor's output deterministically from the
@MangaSourceParser annotations in atlas-parsers/src/main:

  * org/koitharu/kotatsu/parsers/model/MangaParserSource.kt
      - one MangaParserSource object per source, `entries`, `find`, `valueOf`
  * org/koitharu/kotatsu/parsers/MangaParserFactory.kt
      - `MangaParserSource.newRawParser(context)` / `newParser(context)`,
        dispatch split into chunks of 200 (JVM method-size limit)
  * org/koitharu/kotatsu/parsers/core/ParsersBuildInfo.kt
      - VERSION = upstream commit time, REVISION = upstream short hash

Re-run after updating the vendored tree (see atlas-parsers/README.md):

    python3 atlas-parsers/gen_registry.py

Upstream HEAD vendored: c429dceb0a8232dbffe150c6b94ad95fd7ef544a
(2026-11-13, kotatsu-parsers-redo, master).
"""

import re
import sys
from pathlib import Path

CHUNK_SIZE = 200
UPSTREAM_VERSION = 1794578745  # commit time of c429dceb, seconds since epoch
UPSTREAM_REVISION = "c429dceb0a"

MODULE = Path(__file__).resolve().parent
MAIN = MODULE / "src" / "main" / "kotlin"

NAME_PATTERN = re.compile(r"[A-Z_][A-Z0-9_]{3,}")
def scan_file(text: str):
    """Yield (arg_text, start, end, enclosing) per @MangaSourceParser(...).

    A plain regex cannot do this: titles may contain parens and commas
    inside string literals, and some parser classes are nested inside an
    abstract base class (e.g. `LineWebtoonsParser.German`), so the scan
    tracks brace nesting — skipping strings and comments — and records the
    enclosing class/object stack at each annotation.
    """
    results = []
    stack: list = []
    pending = None
    i, n = 0, len(text)
    while i < n:
        ch = text[i]
        if text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j < 0 else j
            continue
        if text.startswith("/*", i):
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            i = n if j < 0 else j + 3
            continue
        if ch == '"':
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == '"':
                    i += 1
                    break
                i += 1
            continue
        if ch == "'":
            i += 1
            while i < n and text[i] != "'":
                i += 2 if text[i] == "\\" else 1
            i += 1
            continue
        if text.startswith("@MangaSourceParser(", i):
            j = i + len("@MangaSourceParser(")
            depth, in_str, esc = 1, False, False
            while j < n and depth > 0:
                c = text[j]
                if in_str:
                    if esc:
                        esc = False
                    elif c == "\\":
                        esc = True
                    elif c == '"':
                        in_str = False
                elif c == '"':
                    in_str = True
                elif c == "(":
                    depth += 1
                elif c == ")":
                    depth -= 1
                j += 1
            results.append((
                text[i + len("@MangaSourceParser("): j - 1],
                i, j,
                [s for s in stack if s],
            ))
            i = j
            continue
        m = re.match(r"(?:class|object)\s+(\w+)", text[i:])
        if m and (i == 0 or not (text[i - 1].isalnum() or text[i - 1] in "_$")):
            pending = m.group(1)
            i += m.end()
            continue
        if ch == "{":
            stack.append(pending)
            pending = None
            i += 1
            continue
        if ch == "}":
            if stack:
                stack.pop()
            i += 1
            continue
        i += 1
    return results
PACKAGE_RE = re.compile(r"^package\s+([\w.]+)", re.MULTILINE)
CLASS_RE = re.compile(
    r"(?:(?:public|internal|open|abstract|final)\s+)*class\s+(\w+)"
)
BLOCK_ANNOTATION_RE = re.compile(r"@(\w+)(?:\((.*?)\))?", re.DOTALL)


def split_top_level(text: str) -> list[str]:
    """Split on commas that are not inside quotes or nested parens."""
    parts, depth, in_str, esc, cur = [], 0, False, False, []
    for ch in text:
        if in_str:
            cur.append(ch)
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
            cur.append(ch)
        elif ch == "(":
            depth += 1
            cur.append(ch)
        elif ch == ")":
            depth -= 1
            cur.append(ch)
        elif ch == "," and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    if cur:
        parts.append("".join(cur))
    return [p.strip() for p in parts if p.strip()]


def parse_string(raw: str) -> str:
    """Return the raw inner text of a Kotlin double-quoted string literal."""
    raw = raw.strip()
    assert raw.startswith('"') and raw.endswith('"'), f"not a string: {raw!r}"
    return raw[1:-1]


def parse_args(arg_text: str) -> dict:
    """Parse @MangaSourceParser(...) args (positional or named)."""
    values: dict[str, str] = {}
    positional = ["name", "title", "locale", "type"]
    for i, part in enumerate(split_top_level(arg_text)):
        m = re.match(r"(\w+)\s*=\s*(.+)$", part, re.DOTALL)
        if m:
            values[m.group(1)] = m.group(2).strip()
        else:
            values[positional[i]] = part
    return values


def collect_entries() -> list[dict]:
    entries: list[dict] = []
    seen: dict[str, str] = {}
    for path in sorted(MAIN.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        package = PACKAGE_RE.search(text)
        pkg = package.group(1) if package else ""
        for arg_text, ann_start, ann_end, enclosing in scan_file(text):
            args = parse_args(arg_text)
            name = parse_string(args["name"])
            title = parse_string(args["title"])
            locale = parse_string(args["locale"]) if "locale" in args else ""
            type_ref = args.get("type", "ContentType.MANGA").strip()
            # Annotations attached to the same class: look at the text from
            # the start of the annotation block (a few lines above) up to the
            # class declaration that follows this annotation.
            after = text[ann_end:]
            class_m = CLASS_RE.search(after)
            if not class_m:
                raise SystemExit(f"{path}: no class after @MangaSourceParser({name})")
            class_name = class_m.group(1)
            block = text[max(0, ann_start - 400): ann_end + class_m.start()]
            annotations = {
                am.group(1): (am.group(2) or "")
                for am in BLOCK_ANNOTATION_RE.finditer(block)
            }
            is_broken = "Broken" in annotations
            deprecation = ""
            if "Deprecated" in annotations:
                reason_m = re.search(r'"((?:[^"\\]|\\.)*)"', annotations["Deprecated"])
                reason = reason_m.group(1) if reason_m else "Unknown reason"
                deprecation = f'@Deprecated("{reason}") '
            if "abstract" in after[: class_m.start()]:
                print(f"WARNING: {path}: {class_name} is abstract, skipping", file=sys.stderr)
                continue
            if not NAME_PATTERN.fullmatch(name):
                raise SystemExit(f"{path}: bad source name {name!r}")
            if name in seen:
                raise SystemExit(
                    f"duplicate source {name}: {seen[name]} and {path}"
                )
            seen[name] = str(path)
            entries.append(
                {
                    "name": name,
                    "title": title,
                    "locale": locale,
                    "type": type_ref,
                    "is_broken": is_broken,
                    "deprecation": deprecation,
                    "class_name": ".".join(
                        [p for p in [pkg] + enclosing + [class_name] if p]
                    ),
                }
            )
    entries.sort(key=lambda e: e["name"])
    return entries


def kotlin_str(raw_inner: str) -> str:
    return f'"{raw_inner}"'


def write_sources(entries: list[dict]) -> None:
    chunks = [entries[i:i + CHUNK_SIZE] for i in range(0, len(entries), CHUNK_SIZE)]
    out: list[str] = []
    out.append("package org.koitharu.kotatsu.parsers.model")
    out.append("")
    out.append("import org.koitharu.kotatsu.parsers.DirectParsersAccess")
    out.append("import java.util.Collections")
    out.append("")
    out.append("// Generated by atlas-parsers/gen_registry.py from the vendored")
    out.append("// kotatsu-parsers-redo tree (HEAD c429dceb0a). DO NOT EDIT BY HAND.")
    out.append("@DirectParsersAccess")
    out.append("public class MangaParserSource private constructor(")
    out.append("\tname: String,")
    out.append("\ttitle: String,")
    out.append("\tlocale: String,")
    out.append("\tcontentType: ContentType,")
    out.append("\tisBroken: Boolean,")
    out.append(") : ParserMangaSource(name, title, locale, contentType, isBroken) {")
    out.append("")
    out.append("\tpublic companion object {")
    out.append("")
    out.append("\t\tprivate val all: Array<MangaParserSource> = arrayOf(")
    for i in range(len(chunks)):
        out.append(f"\t\t\t*sources{i}(),")
    out.append("\t\t)")
    out.append("")
    out.append(
        "\t\tprivate val byName: Map<String, MangaParserSource> = "
        "all.associateByTo(HashMap(all.size * 2)) { it.name }"
    )
    out.append("")
    out.append(
        "\t\tpublic val entries: List<MangaParserSource> = "
        "Collections.unmodifiableList(all.asList())"
    )
    out.append("")
    out.append("\t\tpublic fun find(name: String): MangaParserSource? = byName[name]")
    out.append("")
    out.append("\t\tpublic fun valueOf(name: String): MangaParserSource {")
    out.append(
        '\t\t\treturn byName[name] ?: throw IllegalArgumentException("No manga source named $name")'
    )
    out.append("\t\t}")
    out.append("")
    for i, e in enumerate(entries):
        out.append(
            f"\t\t{e['deprecation']}public val {e['name']}: MangaParserSource get() = all[{i}]"
        )
    for i, chunk in enumerate(chunks):
        out.append("")
        out.append(f"\t\tprivate fun sources{i}(): Array<MangaParserSource> = arrayOf(")
        for e in chunk:
            broken = "true" if e["is_broken"] else "false"
            out.append(
                f"\t\t\tMangaParserSource({kotlin_str(e['name'])}, "
                f"{kotlin_str(e['title'])}, {kotlin_str(e['locale'])}, "
                f"{e['type']}, {broken}),"
            )
        out.append("\t\t)")
    out.append("\t}")
    out.append("}")
    out.append("")
    target = (
        MAIN / "org" / "koitharu" / "kotatsu" / "parsers" / "model"
        / "MangaParserSource.kt"
    )
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text("\n".join(out), encoding="utf-8")


def write_factory(entries: list[dict]) -> None:
    chunks = [entries[i:i + CHUNK_SIZE] for i in range(0, len(entries), CHUNK_SIZE)]
    out: list[str] = []
    out.append("package org.koitharu.kotatsu.parsers")
    out.append("")
    out.append("import org.koitharu.kotatsu.parsers.model.MangaParserSource")
    out.append("import org.koitharu.kotatsu.parsers.core.MangaParserWrapper")
    out.append("")
    out.append("// Generated by atlas-parsers/gen_registry.py from the vendored")
    out.append("// kotatsu-parsers-redo tree (HEAD c429dceb0a). DO NOT EDIT BY HAND.")
    out.append("/** The parser of a source as its class makes it, with nothing put around it. */")
    out.append("internal fun MangaParserSource.newRawParser(context: MangaLoaderContext): MangaParser {")
    dispatch = " ?: ".join(f"newParser{i}(name, context)" for i in range(len(chunks)))
    out.append(f"\tval parser = {dispatch}")
    out.append('\t\t?: throw IllegalArgumentException("No parser for manga source $name")')
    out.append("\trequire(parser.source == this) {")
    out.append('\t\t"Cannot instantiate manga parser: $name mapped to ${parser.source}"')
    out.append("\t}")
    out.append("\treturn parser")
    out.append("}")
    out.append("")
    out.append("internal fun MangaParserSource.newParser(context: MangaLoaderContext): MangaParser {")
    out.append("\treturn MangaParserWrapper(newRawParser(context))")
    out.append("}")
    out.append("")
    for i, chunk in enumerate(chunks):
        out.append(f"private fun newParser{i}(name: String, context: MangaLoaderContext): MangaParser? = when (name) {{")
        for e in chunk:
            out.append(f'\t"{e["name"]}" -> {e["class_name"]}(context)')
        out.append("\telse -> null")
        out.append("}")
        out.append("")
    target = MAIN / "org" / "koitharu" / "kotatsu" / "parsers" / "MangaParserFactory.kt"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text("\n".join(out), encoding="utf-8")


def write_build_info() -> None:
    text = (
        "package org.koitharu.kotatsu.parsers.core\n"
        "\n"
        "// Generated by atlas-parsers/gen_registry.py from the vendored\n"
        "// kotatsu-parsers-redo tree (HEAD c429dceb0a). DO NOT EDIT BY HAND.\n"
        "internal object ParsersBuildInfo {\n"
        "\n"
        f"\tconst val VERSION: Long = {UPSTREAM_VERSION}L\n"
        "\n"
        f'\tconst val REVISION: String = "{UPSTREAM_REVISION}"\n'
        "}\n"
    )
    target = (
        MAIN / "org" / "koitharu" / "kotatsu" / "parsers" / "core"
        / "ParsersBuildInfo.kt"
    )
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")


def main() -> None:
    entries = collect_entries()
    broken = sum(1 for e in entries if e["is_broken"])
    write_sources(entries)
    write_factory(entries)
    write_build_info()
    print(f"sources: {len(entries)} (broken: {broken}, live: {len(entries) - broken})")
    print("generated: MangaParserSource.kt, MangaParserFactory.kt, ParsersBuildInfo.kt")


if __name__ == "__main__":
    main()
