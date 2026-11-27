"""Shared primitives for the extractors.

Everything here is paths, config and parsing machinery. No extractor and no helper in this
package carries a heading, a label, a caption or a sentence: document text lives in
``docs/templates/`` and in the authored segments listed under ``authored_dirs`` in
``docs/scripts/config.json``.

Every path below is read from that config. Nothing here names a repository layout.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import config  # noqa: E402

REPO = config.root()
DOCS = config.DATA["docs_root"]

_SOURCES = config.DATA["sources"]

MAIN_ROOT = _SOURCES["main_root"]
TEST_ROOT = _SOURCES["test_root"]
CHANGELOG = _SOURCES["changeset"]
APP_FRAMEWORK_CONFIG = _SOURCES["app_config"]
APP_CONFIG_FILE = _SOURCES["app_yaml"]
BUILD_FILE = _SOURCES["build"]
E2E_SCENARIO = _SOURCES["e2e_scenario"]
K8S = _SOURCES["k8s"]

JSON_EXAMPLE_SOURCES = tuple(_SOURCES["json_example_sources"])

MEDIA_CONSTANTS = {
    "APPLICATION_JSON_VALUE": "application/json",
    "MediaType.APPLICATION_JSON_VALUE": "application/json",
    "MediaType.TEXT_PLAIN_VALUE": "text/plain",
    "APPLICATION_PDF_VALUE": "application/pdf",
    "MediaType.APPLICATION_PDF_VALUE": "application/pdf",
}

_WHITESPACE = " \t\r\n"

FRAMEWORK_PARAMETER_TYPES = (
    "Pageable", "Sort", "Principal", "Authentication", "HttpServletRequest",
    "HttpServletResponse", "Locale", "ZoneId", "TimeZone", "Model", "Map",
    "WebRequest", "Errors", "BindingResult", "SessionStatus", "Jwt",
)

# An argument bound by one of these annotations arrives from somewhere other than the query string — the
# authenticated principal, a request attribute, a cookie, the session, a matrix variable or a model
# attribute. Without this, a handler's `@AuthenticationPrincipal Jwt caller` is reported as a query
# parameter the client would have to send.
NON_QUERY_BINDINGS = (
    "AuthenticationPrincipal", "RequestAttribute", "CookieValue", "SessionAttribute",
    "MatrixVariable", "ModelAttribute",
)


def read(rel_path: str) -> str:
    return (REPO / rel_path).read_text(encoding="utf-8")


def rel(path: Path) -> str:
    return path.relative_to(REPO).as_posix()


def line_at(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def ref(rel_path: str, start: int, end: int | None = None) -> str:
    """A ``path:line`` or ``path:start-end`` provenance reference."""
    return f"{rel_path}:{start}" if end is None or end == start else f"{rel_path}:{start}-{end}"


def split_top_level(text: str) -> list[str]:
    """Split on commas that are not inside quotes, parens, brackets or braces."""
    parts: list[str] = []
    current = ""
    depth = 0
    quote: str | None = None
    escaped = False
    for ch in text:
        if quote is not None:
            current += ch
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == quote:
                quote = None
            continue
        if ch in "\"'":
            quote = ch
            current += ch
        elif ch in "([{":
            depth += 1
            current += ch
        elif ch in ")]}":
            depth -= 1
            current += ch
        elif ch == "," and depth == 0:
            parts.append(current)
            current = ""
        else:
            current += ch
    if current.strip():
        parts.append(current)
    return [part.strip() for part in parts]


def match_bracket(text: str, start: int) -> int:
    """Return the index of the closing bracket for the opener at ``start``."""
    depth = 0
    quote: str | None = None
    escaped = False
    index = start
    while index < len(text):
        ch = text[index]
        if quote is not None:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == quote:
                quote = None
        elif ch in "\"'":
            quote = ch
        elif ch in "([{":
            depth += 1
        elif ch == text[start] or ch in ")]}":
            depth -= 1
            if depth == 0:
                return index
        index += 1
    return len(text) - 1


def java_files(root: str = MAIN_ROOT):
    return sorted((REPO / root).rglob("*.java"))


def resolve_media(value: str) -> str:
    value = value.strip()
    if value.startswith("{") and value.endswith("}"):
        return ", ".join(resolve_media(part) for part in split_top_level(value[1:-1]))
    if len(value) >= 2 and value[0] == '"' and value[-1] == '"':
        return value[1:-1]
    return MEDIA_CONSTANTS.get(value, value)


def parse_annotation_args(args_text: str) -> dict:
    out = {"path": None, "params": [], "produces": None, "consumes": None, "positional": []}
    for part in split_top_level(args_text):
        if not part:
            continue
        if "=" in part and not part.lstrip().startswith(("{", "[", '"')):
            key, _, value = part.partition("=")
            key, value = key.strip(), value.strip()
            if key in ("path", "value"):
                out["path"] = resolve_media(value)
            elif key == "params":
                inner = value[1:-1] if value.startswith("{") else value
                out["params"] = [p.strip().strip('"') for p in split_top_level(inner) if p.strip()]
            elif key in ("produces", "consumes"):
                out[key] = resolve_media(value)
        else:
            out["positional"].append(part)
    if out["path"] is None and out["positional"]:
        out["path"] = resolve_media(out["positional"][0])
    return out


def parse_params(params_text: str) -> list[dict]:
    params = []
    for raw in split_top_level(params_text):
        if not raw:
            continue
        annotations = re.findall(r"@(\w+)", raw)
        core = re.sub(r"@\w+(\([^()]*\))?", "", raw).strip()
        tokens = core.split()
        if len(tokens) >= 2:
            type_name, name = " ".join(tokens[:-1]), tokens[-1]
        elif tokens:
            type_name, name = tokens[0], None
        else:
            type_name, name = "", None
        params.append({"annotations": annotations, "type": type_name, "name": name})
    return params


def parse_yaml(text: str):
    """Parse YAML with PyYAML, so nested, quoted and multi-document files are never misread."""
    documents = [document for document in yaml.safe_load_all(text) if document is not None]
    if not documents:
        return None
    return documents[0] if len(documents) == 1 else documents


def yaml_key_line(text: str, key: str) -> int:
    """The first line declaring ``key``, for provenance of a configuration value.

    Fails loudly rather than falling back to a plausible line: a reference that points at the
    first line of a file proves nothing, and a silent fallback would hide exactly that.
    """
    lines = text.splitlines()
    segments = key.split(".")
    while segments:
        candidate = ".".join(segments)
        for position, line in enumerate(lines, start=1):
            if re.match(r"^\s*(?:-\s+)?" + re.escape(candidate) + r":", line):
                return position
        segments = segments[1:]
    raise SystemExit(f"cannot locate {key!r} in the configuration it is read from")


def _is_query_parameter(param: dict) -> bool:
    """A request parameter is bound from the query string, never from the body, a binding or the framework."""
    if "PathVariable" in param["annotations"] or "RequestBody" in param["annotations"]:
        return False
    if any(binding in param["annotations"] for binding in NON_QUERY_BINDINGS):
        return False
    return param["type"].strip() not in FRAMEWORK_PARAMETER_TYPES


def yaml_value_line(text: str, value: str) -> int:
    """The first line that names ``value`` as a scalar, for a Kubernetes-style ``name:`` entry."""
    for position, line in enumerate(text.splitlines(), start=1):
        if re.match(r"^\s*(?:-\s+)?[\w.-]+:\s*" + re.escape(value) + r"\s*$", line):
            return position
    raise SystemExit(f"cannot locate the declaration of {value!r} in the manifest")


JAVADOC_TAG = re.compile(r"\{@\w+\s+([^}]*)\}")
JAVADOC_SENTENCE = re.compile(r"(?<=[.!?])\s+")


def _javadoc_text(line: str, first: bool) -> str:
    """One Javadoc line as a reader sees it: no delimiters, no margin star, no inline tag."""
    content = line.strip()
    if first:
        content = content.split("/**", 1)[1]
    content = content.removesuffix("*/").strip().removeprefix("*").strip()
    for prefix in ("</p>", "<p>"):
        content = content.removeprefix(prefix).strip()
    return JAVADOC_TAG.sub(lambda tag: tag.group(1), content)


def javadoc(rel_path: str, line: int) -> dict | None:
    """The Javadoc block directly above ``line``, split into its first sentence and the rest.

    ``line`` is the annotation or declaration the block documents; the block is looked for above
    that line and above the annotations stacked on it. A block that is not there returns ``None``,
    so a caller records a gap instead of quoting the Javadoc of a neighbouring member. Both parts
    carry the line they start on.
    """
    lines = read(rel_path).splitlines()
    index = line - 2
    while index >= 0 and (not lines[index].strip() or lines[index].strip().startswith("@")):
        index -= 1
    if index < 0 or not lines[index].strip().endswith("*/"):
        return None
    end = index
    while index >= 0 and "/**" not in lines[index]:
        if index < end and lines[index].strip().endswith("*/"):
            return None
        index -= 1
    if index < 0:
        return None
    paragraphs: list[dict] = []
    current: list[str] = []
    start = 0
    for number in range(index, end + 1):
        text = _javadoc_text(lines[number], number == index)
        if text.startswith("@"):
            break
        if not text:
            if current:
                paragraphs.append({"line": start, "text": " ".join(current)})
                current = []
            continue
        if not current:
            start = number + 1
        current.append(text)
    if current:
        paragraphs.append({"line": start, "text": " ".join(current)})
    if not paragraphs:
        return None
    sentences = JAVADOC_SENTENCE.split(paragraphs[0]["text"])
    rest = sentences[1:] + [paragraph["text"] for paragraph in paragraphs[1:]]
    return {
        "summary": sentences[0],
        "summary_line": paragraphs[0]["line"],
        "description": " ".join(rest),
        "description_line": paragraphs[1]["line"] if len(paragraphs) > 1 else paragraphs[0]["line"],
    }


def controller_operations() -> list[dict]:
    """Every Spring request mapping declared by a ``@RestController``, in source order."""
    operations: list[dict] = []
    for path in java_files():
        text = path.read_text(encoding="utf-8")
        if "@RestController" not in text:
            continue
        file_rel = rel(path)
        class_line = line_at(text, text.index("@RestController"))
        class_prefix = ""
        class_match = re.search(r"@RequestMapping\s*\(", text[: text.find("{")])
        if class_match:
            open_paren = text.index("(", class_match.start())
            class_prefix = parse_annotation_args(text[open_paren + 1 : match_bracket(text, open_paren)])["path"] or ""

        for match in re.finditer(r"@(Get|Post|Put|Patch|Delete|Request)Mapping", text):
            index = match.end()
            while index < len(text) and text[index] in _WHITESPACE:
                index += 1
            if index >= len(text) or text[index] != "(":
                continue
            args = parse_annotation_args(text[index + 1 : match_bracket(text, index)])
            tail = text[match_bracket(text, index) + 1 :]
            signature = re.search(r"\n\s*([^\n(]+?)\s+(\w+)\s*\(", tail)
            if not signature:
                continue
            return_type, handler = signature.group(1).strip(), signature.group(2)
            params_open = tail.find("(", signature.start(2))
            params = parse_params(tail[params_open + 1 : match_bracket(tail, params_open)])
            query = [p["name"] for p in params if p["name"] and _is_query_parameter(p)]
            body = next((p for p in params if "RequestBody" in p["annotations"]), None)
            operations.append(
                {
                    "method": {"Get": "GET", "Post": "POST", "Put": "PUT", "Patch": "PATCH",
                               "Delete": "DELETE", "Request": "ANY"}[match.group(1)],
                    "path": (class_prefix + (args["path"] or "")).strip() or "/",
                    "handler": handler,
                    "return_type": return_type,
                    "request_type": body["type"] if body else None,
                    "consumes": [part for part in (args["consumes"] or "").split(", ") if part],
                    "produces": [part for part in (args["produces"] or "").split(", ") if part],
                    "mapping_params": args["params"],
                    "path_params": [p["name"] for p in params if "PathVariable" in p["annotations"]],
                    "query_params": query,
                    "source": {"path": file_rel, "line": line_at(text, match.start()),
                               "signature_line": line_at(text, match_bracket(text, index) + 1
                                                          + signature.start(2)),
                               "class_line": class_line},
                }
            )
    return operations


def normalise_segment(segment: str) -> str:
    return segment.replace("-", "").replace("_", "").lower()


def normalise_path(path: str) -> str:
    return "/" + "/".join(normalise_segment(part) for part in path.strip("/").split("/") if part)


def java_text_blocks(rel_path: str) -> list[dict]:
    """Every Java text block in a file, with its span, its de-indented content and its context."""
    text = read(rel_path)
    lines = text.splitlines()
    blocks: list[dict] = []
    index = 0
    while index < len(lines):
        if '"""' not in lines[index]:
            index += 1
            continue
        opener = lines[index]
        if opener.count('"""') >= 2:
            blocks.append({"start": index + 1, "end": index + 1,
                           "content": opener[opener.index('"""') + 3 : opener.rindex('"""')],
                           "opener": opener})
            index += 1
            continue
        end = index + 1
        while end < len(lines) and '"""' not in lines[end]:
            end += 1
        body = lines[index + 1 : end]
        blocks.append({"start": index + 1, "end": end + 1,
                       "content": "\n".join(_dedent(body)), "opener": opener})
        index = end + 1
    for block in blocks:
        block["class"], block["method"] = _enclosing(lines, block["start"] - 1)
    return blocks


def _dedent(lines: list[str]) -> list[str]:
    indents = [len(line) - len(line.lstrip(" ")) for line in lines if line.strip()]
    strip = min(indents) if indents else 0
    return [line[strip:] if line.strip() else "" for line in lines]


def _enclosing(lines: list[str], position: int) -> tuple[str | None, str | None]:
    class_name = method = None
    for line in reversed(lines[:position]):
        if method is None and re.match(r"\s+\w[\w<>\[\], .]*\s+\w+\(", line) and "(" in line:
            method = re.search(r"(\w+)\(", line).group(1)
        if class_name is None:
            class_match = re.search(r"\b(?:class|interface|record)\s+(\w+)", line)
            if class_match:
                class_name = class_match.group(1)
                break
    return class_name, method


def looks_like_json(content: str) -> bool:
    return content.lstrip().startswith(("{", "["))


def parse_json(content: str):
    """Parse a JSON example, tolerating the ``%s`` placeholder the assertions substitute."""
    for candidate in (content, content.replace("%s", "PLACEHOLDER")):
        try:
            return json.loads(candidate)
        except json.JSONDecodeError:
            continue
    return None


def field_paths(value, prefix: str = "", depth: int = 0) -> list[str]:
    """Dot and bracket notation paths of a parsed JSON value, four levels deep."""
    paths: list[str] = []
    if depth >= 4:
        return paths
    if isinstance(value, list):
        if value:
            paths.extend(field_paths(value[0], prefix + "[]", depth + 1))
        return paths
    if isinstance(value, dict):
        for key, nested in value.items():
            path = f"{prefix}.{key}" if prefix else key
            paths.append(path)
            paths.extend(field_paths(nested, path, depth + 1))
    return paths
