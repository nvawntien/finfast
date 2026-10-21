"""Check FinFast's local documentation links and every OpenAPI JSON pointer.

Run from any directory after installing requirements-docs.txt. Remote URLs are
not fetched. This checks document structure, not application correctness.
"""

from collections import Counter
from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlsplit

from markdown_it import MarkdownIt
import yaml


ROOT = Path(__file__).resolve().parents[1]
PARSER = MarkdownIt("commonmark").enable("table")


def inline_text(tokens):
    return "".join(
        token.content for token in tokens if token.type in {"text", "code_inline"}
    )


def heading_ids(tokens):
    counts = Counter()
    result = set()
    for index, token in enumerate(tokens):
        if token.type != "heading_open":
            continue
        title = inline_text(tokens[index + 1].children or []).lower()
        slug = re.sub(r"[^\w\- ]", "", title).replace(" ", "-")
        suffix = f"-{counts[slug]}" if counts[slug] else ""
        counts[slug] += 1
        result.add(slug + suffix)
    return result


class UniqueKeyLoader(yaml.SafeLoader):
    """Fail on duplicate YAML keys instead of silently keeping the last value."""


def unique_mapping(loader, node, deep=False):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in result:
            raise ValueError(f"duplicate YAML key {key!r} at line {key_node.start_mark.line + 1}")
        result[key] = loader.construct_object(value_node, deep=deep)
    return result


UniqueKeyLoader.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique_mapping
)


def main():
    errors = []
    documents = {
        path.resolve(): PARSER.parse(path.read_text(encoding="utf-8"))
        for path in [ROOT / "README.md", *sorted((ROOT / "docs").rglob("*.md"))]
    }
    anchors = {path: heading_ids(tokens) for path, tokens in documents.items()}
    links = 0
    for path, tokens in documents.items():
        for block in tokens:
            for token in block.children or []:
                attribute = "href" if token.type == "link_open" else "src" if token.type == "image" else None
                if attribute is None:
                    continue
                link = token.attrGet(attribute)
                url = urlsplit(link)
                if url.scheme or url.netloc:
                    continue
                links += 1
                target = (path.parent / unquote(url.path)).resolve() if url.path else path
                label = f"{path.relative_to(ROOT)}: {link}"
                if not target.exists():
                    errors.append(f"Missing target: {label}")
                elif url.fragment and target.suffix == ".md":
                    if target not in anchors:
                        anchors[target] = heading_ids(PARSER.parse(target.read_text(encoding="utf-8")))
                    if unquote(url.fragment) not in anchors[target]:
                        errors.append(f"Missing heading: {label}")

    spec_path = ROOT / "docs/03-design/contracts/openapi-v1.yaml"
    spec = yaml.load(spec_path.read_text(encoding="utf-8"), Loader=UniqueKeyLoader)
    refs = 0

    def visit(value):
        nonlocal refs
        if isinstance(value, dict):
            if "$ref" in value:
                refs += 1
                reference = value["$ref"]
                if not reference.startswith("#/"):
                    errors.append(f"Reference requires an explicit resolver: {reference}")
                else:
                    current = spec
                    try:
                        for part in unquote(reference[2:]).split("/"):
                            key = part.replace("~1", "/").replace("~0", "~")
                            current = current[int(key)] if isinstance(current, list) else current[key]
                    except (KeyError, IndexError, ValueError, TypeError):
                        errors.append(f"Unresolved OpenAPI reference: {reference}")
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)

    visit(spec)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"PASS: {len(documents)} Markdown files, {links} local links, {refs} OpenAPI references; no duplicate YAML keys.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
