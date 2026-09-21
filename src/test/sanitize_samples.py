#!/usr/bin/env python3
"""
One-off generator: reads real Notion API captures from src/test/real-samples/
(gitignored, contains real personal content) and produces structurally
identical fixtures in src/test/resources/ with placeholder text/ids/timestamps.

Not part of the Java build - this is a throwaway tool to produce the
checked-in fixtures, run once and discarded.
"""
import json
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[2] # if ran from src/test, resolves to root
REAL_DIR = REPO / "src/test/real-samples"
OUT_DIR = REPO / "src/test/resources"

# Ordered so relation ids referenced later files were already assigned a
# placeholder when the entity they point at was first seen.
FILES = [
    "notion-sample-long-term.json",
    "notion-sample-tasks.json",
    "notion-sample-minutes.json",
]

UUID_RE = re.compile(r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', re.IGNORECASE)

id_map = {}
id_counter = [0]


def placeholder_id(orig):
    if orig in id_map:
        return id_map[orig]
    id_counter[0] += 1
    n = id_counter[0]
    if UUID_RE.match(orig):
        ph = f"00000000-0000-4000-8000-{n:012d}"
    else:
        ph = f"prop{n:04d}"
    id_map[orig] = ph
    return ph


ts_map = {}
ts_counter = [0]


def placeholder_timestamp(orig):
    if orig in ts_map:
        return ts_map[orig]
    ts_counter[0] += 1
    n = ts_counter[0]
    day = (n % 28) + 1
    if "T" in orig:
        ph = f"2024-01-{day:02d}T00:00:00.000Z"
    else:
        ph = f"2024-01-{day:02d}"
    ts_map[orig] = ph
    return ph


text_counter = {}


def placeholder_text(prop_name):
    text_counter[prop_name] = text_counter.get(prop_name, 0) + 1
    return f"Placeholder {prop_name} {text_counter[prop_name]}"


url_counter = [0]


def placeholder_url():
    url_counter[0] += 1
    return f"https://app.notion.com/p/Sample-Page-{url_counter[0]:04d}"


ID_KEY_NAMES = {"id", "database_id", "request_id"}
TIMESTAMP_KEY_NAMES = {"created_time", "last_edited_time", "start", "end"}


def is_text_leaf(node):
    return (
        isinstance(node, dict)
        and node.get("type") == "text"
        and isinstance(node.get("text"), dict)
        and "content" in node["text"]
        and "plain_text" in node
    )


def nearest_property_name(path):
    for iGNOREIGNORECASE in range(len(path) - 1):
      if path[iGNOREIGNORECASE] == "properties":
        return path[iGNOREIGNORECASE + 1]
    return "Text"


def handle_text_leaf(node, path):
    content = node["text"].get("content", "")
    ph = placeholder_text(nearest_property_name(path)) if content else content
    result = {}
    for k, v in node.items():
        if k == "text":
            new_text = dict(v)
            new_text["content"] = ph
            result[k] = walk(new_text, path + ["text"])
        elif k == "plain_text":
            result[k] = ph
        else:
            result[k] = walk(v, path + [k])
    return result


def walk(node, path):
    if isinstance(node, dict):
        if is_text_leaf(node):
            return handle_text_leaf(node, path)
        result = {}
        for k, v in node.items():
            if k in ID_KEY_NAMES and isinstance(v, str) and v:
                result[k] = placeholder_id(v)
            elif k in TIMESTAMP_KEY_NAMES and isinstance(v, str) and v:
                result[k] = placeholder_timestamp(v)
            elif k == "url" and isinstance(v, str) and v:
                result[k] = placeholder_url()
            else:
                result[k] = walk(v, path + [k])
        return result
    elif isinstance(node, list):
        return [walk(item, path) for item in node]
    else:
        return node


def generate():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name in FILES:
        real_path = REAL_DIR / name
        out_path = OUT_DIR / name
        data = json.loads(real_path.read_text())
        scrubbed = walk(data, [])
        out_path.write_text(json.dumps(scrubbed, indent=2, ensure_ascii=False) + "\n")
        print(f"wrote {out_path}")


# ---------------------------------------------------------------------------
# Validation: recursively compare key/type structure of real vs generated.
# ---------------------------------------------------------------------------

def json_type(v):
    if isinstance(v, bool):
        return "bool"
    if isinstance(v, dict):
        return "dict"
    if isinstance(v, list):
        return "list"
    if isinstance(v, (int, float)):
        return "number"
    if isinstance(v, str):
        return "string"
    if v is None:
        return "null"
    return type(v).__name__


def compare(a, b, path, mismatches):
    ta, tb = json_type(a), json_type(b)
    if ta != tb:
        mismatches.append(f"{path or '<root>'}: type {ta} != {tb}")
        return
    if ta == "dict":
        ka, kb = set(a.keys()), set(b.keys())
        if ka != kb:
            missing = ka - kb
            extra = kb - ka
            mismatches.append(
                f"{path or '<root>'}: key mismatch (missing in fixture: {sorted(missing)}, "
                f"extra in fixture: {sorted(extra)})"
            )
        for k in ka & kb:
            compare(a[k], b[k], f"{path}.{k}" if path else k, mismatches)
    elif ta == "list":
        if len(a) != len(b):
            mismatches.append(f"{path}: list length {len(a)} != {len(b)}")
        for iGNOREIGNORECASE, (ia, ib) in enumerate(zip(a, b)):
          compare(ia, ib, f"{path}[{iGNOREIGNORECASE}]", mismatches)
    # scalars: type equality already checked above, no value comparison needed


def validate():
    all_ok = True
    for name in FILES:
        real = json.loads((REAL_DIR / name).read_text())
        fixture = json.loads((OUT_DIR / name).read_text())
        mismatches = []
        compare(real, fixture, "", mismatches)
        if mismatches:
            all_ok = False
            print(f"FAIL {name}: {len(mismatches)} structural mismatch(es)")
            for m in mismatches[:20]:
                print(f"  - {m}")
        else:
            print(f"PASS {name}: structure matches real sample")
    return all_ok


if __name__ == "__main__":
    generate()
    print()
    ok = validate()
    print()
    print("ALL STRUCTURES MATCH" if ok else "STRUCTURAL MISMATCHES FOUND")
    raise SystemExit(0 if ok else 1)
