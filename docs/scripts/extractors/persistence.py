"""Relational model and adapter mapping, parsed from the Liquibase changelog and the JPA entities.

Starting point: a Liquibase changelog that may be split into ``include`` fragments, plus the entities
that map the tables. A change set belongs to the changelog in the position its ``include`` occupies,
so the fragments are resolved and expanded in include order — the order Liquibase itself applies them.
Line numbers come from the composed YAML nodes, so a fact cites the fragment it was declared in.
Point ``sources.changeset`` and ``sources.main_root`` in ``docs/scripts/config.json`` at another
schema and re-map the parsing for another migration tool.
"""

from __future__ import annotations

import posixpath
import re
from pathlib import PurePosixPath

import yaml

from . import common


def _line(node: yaml.Node) -> int:
    return node.start_mark.line + 1


def _mapping(node: yaml.Node | None) -> dict[str, tuple[yaml.Node, yaml.Node]]:
    if not isinstance(node, yaml.MappingNode):
        return {}
    return {key.value: (key, value) for key, value in node.value}


def _sequence(node: tuple[yaml.Node, yaml.Node] | None) -> list[yaml.Node]:
    if node is None or not isinstance(node[1], yaml.SequenceNode):
        return []
    return node[1].value


def _plain(node: yaml.Node | None):
    """A composed node as the value PyYAML would have produced for it."""
    if isinstance(node, yaml.MappingNode):
        return {key.value: _plain(value) for key, value in node.value}
    if isinstance(node, yaml.SequenceNode):
        return [_plain(item) for item in node.value]
    if isinstance(node, yaml.ScalarNode):
        return node.value
    return None


def _resolve_include(rel_path: str, node: yaml.Node) -> str:
    """The file an ``include`` names, resolved the way Liquibase resolves it.

    ``relativeToChangelogFile: true`` resolves beside the changelog that names it; otherwise the path
    is relative to the classpath root, which is the ancestor of the changelog the file is actually
    found under. The nearest ancestor wins, and a fragment that resolves nowhere fails the extraction.
    """
    fields = _mapping(node)
    if "file" not in fields:
        raise SystemExit(f"{rel_path}:{_line(node)}: include names no file")
    name = fields["file"][1].value
    relative = fields.get("relativeToChangelogFile")
    if relative is not None and str(relative[1].value).lower() == "true":
        candidates = [posixpath.dirname(rel_path)]
    else:
        parts = PurePosixPath(rel_path).parts
        candidates = ["/".join(parts[:length]) for length in range(len(parts) - 1, 0, -1)]
    for base in candidates:
        candidate = posixpath.normpath(posixpath.join(base, name))
        if (common.REPO / candidate).is_file():
            return candidate
    raise SystemExit(f"{rel_path}:{_line(node)}: include {name!r} resolves to no file of the repository")


def _default(column: dict) -> dict | None:
    for key, kind in (("defaultValueNumeric", "numeric"), ("defaultValueBoolean", "boolean"),
                      ("defaultValue", "text")):
        value = column.get(key)
        if value is not None:
            return {"kind": kind, "value": value if kind != "text" else str(value)}
    return None


def _column(raw: dict, line: int, path: str) -> dict:
    constraints = raw.get("constraints") or {}
    foreign_key = None
    if constraints.get("foreignKeyName"):
        foreign_key = {"name": constraints["foreignKeyName"],
                       "references": constraints.get("references")}
    return {
        "name": raw.get("name"),
        "type": raw.get("type"),
        "jsonb": raw.get("type") == "jsonb",
        "nullable": constraints.get("nullable"),
        "primary_key": bool(constraints.get("primaryKey")),
        "unique": bool(constraints.get("unique")),
        "default": _default(raw),
        "foreign_key": foreign_key,
        "path": path,
        "line": line,
    }


def _column_declarations(declaring: yaml.Node) -> list[tuple[yaml.Node, dict]]:
    """The ``- column:`` items of a ``createTable`` or ``addColumn`` change, with their plain value."""
    found = []
    for item in _sequence(_mapping(declaring).get("columns")):
        declared = _mapping(item).get("column")
        if declared is not None:
            found.append((item, _plain(declared[1])))
    return found


def _column_record(rel_path: str, declared: tuple[yaml.Node, dict], changeset_id: str) -> dict:
    """One column of the schema, citing the line of the change set that names it."""
    item, raw = declared
    named = _mapping(_mapping(item)["column"][1]).get("name")
    column = _column(raw, _line(named[0]) if named is not None else _line(item), rel_path)
    column["changeset"] = changeset_id
    return column


def _table(rel_path: str, created: yaml.Node, changeset_id: str) -> dict:
    fields = _mapping(created)
    name = _plain(fields["tableName"][1]) if "tableName" in fields else None
    table_line = _line(fields["tableName"][0]) if "tableName" in fields else _line(created)
    return {
        "name": name,
        "changeset": changeset_id,
        "path": rel_path,
        "line": table_line,
        "columns": [_column_record(rel_path, declared, changeset_id)
                    for declared in _column_declarations(created)],
        "evidence": [common.ref(rel_path, table_line)],
    }


def _known(schema: dict[str, dict], rel_path: str, fields: dict, change: str) -> dict:
    """The table a change alters, or a failure naming the file and the line that names it."""
    declared = fields.get("tableName")
    if declared is None:
        raise SystemExit(f"{rel_path}:{_line(fields[list(fields)[0]][0])}: {change} names no table")
    name = _plain(declared[1])
    if name not in schema:
        raise SystemExit(f"{rel_path}:{_line(declared[0])}: {change} alters {name!r}, which no change "
                         f"set of this changelog creates")
    return schema[name]


def _add_columns(rel_path: str, node: yaml.Node, changeset_id: str, schema: dict[str, dict]) -> None:
    fields = _mapping(node)
    table = _known(schema, rel_path, fields, "addColumn")
    table["columns"].extend(_column_record(rel_path, declared, changeset_id)
                            for declared in _column_declarations(node))


def _drop_column(rel_path: str, node: yaml.Node, changeset_id: str,
                 schema: dict[str, dict], dropped: list[dict]) -> None:
    fields = _mapping(node)
    table = _known(schema, rel_path, fields, "dropColumn")
    named = fields.get("columnName")
    if named is None:
        raise SystemExit(f"{rel_path}:{_line(node)}: dropColumn names no column")
    name, line = _plain(named[1]), _line(named[0])
    removed = next((column for column in table["columns"] if column["name"] == name), None)
    if removed is None:
        raise SystemExit(f"{rel_path}:{line}: dropColumn removes {name!r} from {table['name']!r}, "
                         f"which holds no such column")
    table["columns"].remove(removed)
    dropped.append({"table": table["name"], "column": name, "changeset": changeset_id,
                    "path": rel_path, "line": line, "evidence": [common.ref(rel_path, line)]})


def _rename_column(rel_path: str, node: yaml.Node, changeset_id: str,
                   schema: dict[str, dict], renamed: list[dict]) -> None:
    fields = _mapping(node)
    table = _known(schema, rel_path, fields, "renameColumn")
    for key in ("oldColumnName", "newColumnName"):
        if key not in fields:
            raise SystemExit(f"{rel_path}:{_line(node)}: renameColumn declares no {key}")
    old, new = _plain(fields["oldColumnName"][1]), _plain(fields["newColumnName"][1])
    line = _line(fields["newColumnName"][0])
    position = next((index for index, column in enumerate(table["columns"])
                     if column["name"] == old), None)
    if position is None:
        raise SystemExit(f"{rel_path}:{line}: renameColumn renames {old!r} of {table['name']!r}, "
                         f"which holds no such column")
    column = dict(table["columns"][position], name=new, path=rel_path, line=line,
                  changeset=changeset_id)
    if "columnDataType" in fields:
        column["type"] = _plain(fields["columnDataType"][1])
        column["jsonb"] = column["type"] == "jsonb"
    table["columns"][position] = column
    renamed.append({"table": table["name"], "from": old, "to": new, "changeset": changeset_id,
                    "path": rel_path, "line": line, "evidence": [common.ref(rel_path, line)]})


def _changeset(rel_path: str, node: yaml.Node, schema: dict[str, dict],
               dropped: list[dict], renamed: list[dict]) -> dict:
    """One change set, applied to the schema the change sets before it leave behind."""
    fields = _mapping(node)
    identifier = _plain(fields["id"][1]) if "id" in fields else None
    author = _plain(fields["author"][1]) if "author" in fields else None
    identifier_line = _line(fields["id"][0]) if "id" in fields else _line(node)
    tables = []
    for change in _sequence(fields.get("changes")):
        declared = _mapping(change)
        if "createTable" in declared:
            table = _table(rel_path, declared["createTable"][1], identifier)
            schema[table["name"]] = table
            tables.append(table)
        elif "addColumn" in declared:
            _add_columns(rel_path, declared["addColumn"][1], identifier, schema)
        elif "dropColumn" in declared:
            _drop_column(rel_path, declared["dropColumn"][1], identifier, schema, dropped)
        elif "renameColumn" in declared:
            _rename_column(rel_path, declared["renameColumn"][1], identifier, schema, renamed)
    return {
        "changeset": {
            "id": identifier,
            "author": author,
            "path": rel_path,
            "line": identifier_line,
            "tables": [table["name"] for table in tables],
            "column_count": sum(len(table["columns"]) for table in tables),
            "evidence": [common.ref(rel_path, identifier_line)],
        },
        "tables": tables,
    }


def _entries(rel_path: str, schema: dict[str, dict], dropped: list[dict], renamed: list[dict],
             visited: tuple[str, ...] = ()) -> list[dict]:
    """The change sets of one changelog file, its ``include`` fragments expanded where they appear."""
    if rel_path in visited:
        raise SystemExit(f"{rel_path}: include cycle through {', '.join(visited)}")
    root = yaml.compose(common.read(rel_path))
    document = _mapping(root).get("databaseChangeLog")
    if document is None:
        return []
    entries: list[dict] = []
    for item in _sequence(document):
        fields = _mapping(item)
        if "changeSet" in fields:
            entries.append(_changeset(rel_path, fields["changeSet"][1], schema, dropped, renamed))
        elif "include" in fields:
            entries.extend(_entries(_resolve_include(rel_path, fields["include"][1]),
                                    schema, dropped, renamed, visited + (rel_path,)))
    return entries


def changelog() -> dict:
    """The change sets in the order Liquibase applies them, and the schema they leave behind."""
    schema: dict[str, dict] = {}
    dropped: list[dict] = []
    renamed: list[dict] = []
    entries = _entries(common.CHANGELOG, schema, dropped, renamed)
    return {"path": common.CHANGELOG, "changesets": entries, "schema": schema,
            "dropped": dropped, "renamed": renamed}


def tables(changelog_result: dict) -> list[dict]:
    return [table for entry in changelog_result["changesets"] for table in entry["tables"]]


def changesets(changelog_result: dict) -> list[dict]:
    return [entry["changeset"] for entry in changelog_result["changesets"]]


def table_annotations() -> list[dict]:
    """Every ``@Table(name = ...)`` declaration, in file order."""
    found: list[dict] = []
    for path in common.java_files():
        text = path.read_text(encoding="utf-8")
        if "@Table(" not in text:
            continue
        for match in re.finditer(r'@Table\(\s*name\s*=\s*"([^"]+)"', text):
            found.append({"table": match.group(1), "path": common.rel(path),
                          "line": common.line_at(text, match.start())})
    return found


FIELD_DECLARATION = re.compile(
    r"(?:private|protected|public)\s+(?:final\s+)?([\w.<>\[\], ]+?)\s+(\w+)\s*(?:=|;)"
)
COLUMN_NAME = re.compile(r'@Column\(\s*name\s*=\s*"([^"]+)"')


def _snake(name: str) -> str:
    """The column name Hibernate's default physical naming strategy gives a field name."""
    return re.sub(r"(?<=[a-z0-9])(?=[A-Z])", "_", name).lower()


def entity_json_fields() -> list[dict]:
    """The column of every JPA field annotated as JSON, deduplicated per table and column.

    The column name is the one ``@Column`` declares; a field without that annotation names its column
    through the naming strategy, and the record says so instead of assuming the two spellings agree.
    """
    found: list[dict] = []
    seen: dict[tuple[str, str], dict] = {}
    for path in common.java_files():
        text = path.read_text(encoding="utf-8")
        if "@JdbcTypeCode(SqlTypes.JSON)" not in text:
            continue
        lines = text.splitlines()
        current_table = None
        for index, line in enumerate(lines):
            table_match = re.search(r'@Table\(\s*name\s*=\s*"([^"]+)"', line)
            if table_match:
                current_table = table_match.group(1)
            if "@JdbcTypeCode(SqlTypes.JSON)" not in line or not current_table:
                continue
            declared = None
            for candidate in range(index + 1, min(index + 6, len(lines))):
                column_match = COLUMN_NAME.search(lines[candidate])
                if column_match and declared is None:
                    declared = column_match.group(1)
                field_match = FIELD_DECLARATION.search(lines[candidate])
                if not field_match:
                    continue
                column = declared or _snake(field_match.group(2))
                key = (current_table, column)
                entry = seen.get(key)
                if entry is None:
                    seen[key] = {
                        "table": current_table,
                        "column": column,
                        "column_source": "annotation" if declared else "field",
                        "field": field_match.group(2),
                        "field_type": field_match.group(1).strip(),
                        "paths": [{"path": common.rel(path), "line": candidate + 1}],
                    }
                else:
                    entry["paths"].append({"path": common.rel(path), "line": candidate + 1})
                break
    for entry in seen.values():
        entry["path"] = entry["paths"][0]["path"]
        entry["line"] = entry["paths"][0]["line"]
        entry["evidence"] = [common.ref(item["path"], item["line"]) for item in entry["paths"]]
        found.append(entry)
    return sorted(found, key=lambda item: (item["table"], item["column"]))


def json_columns(changelog_result: dict | None = None) -> list[dict]:
    """Every JSONB column, from the changelog and from the entities that declare it, merged."""
    declared = tables(changelog_result or changelog())
    fields = {(item["table"], item["column"]): item for item in entity_json_fields()}
    merged: list[dict] = []
    seen: set[tuple[str, str]] = set()
    for table in declared:
        for column in table["columns"]:
            if not column["jsonb"]:
                continue
            key = (table["name"], column["name"])
            seen.add(key)
            field = fields.get(key)
            entry = {
                "table": table["name"],
                "column": column["name"],
                "type": column["type"],
                "changeset": column["changeset"],
                "changelog_line": column["line"],
                "changelog_path": column["path"],
                "field_type": field["field_type"] if field else None,
                "field": field["field"] if field else None,
                "column_source": field["column_source"] if field else None,
                "entity_paths": field["paths"] if field else [],
                "path": column["path"],
                "line": column["line"],
                "evidence": [common.ref(column["path"], column["line"])]
                + (field["evidence"] if field else []),
            }
            merged.append(entry)
    for key, field in fields.items():
        if key in seen:
            continue
        merged.append({
            "table": key[0],
            "column": key[1],
            "type": None,
            "changeset": None,
            "changelog_line": None,
            "changelog_path": None,
            "field_type": field["field_type"],
            "field": field["field"],
            "column_source": field["column_source"],
            "entity_paths": field["paths"],
            "path": field["path"],
            "line": field["line"],
            "evidence": field["evidence"],
        })
    return merged


def extract() -> dict:
    result = changelog()
    registered = tables(result)
    annotated = table_annotations()
    return {
        "changelog": {"path": common.CHANGELOG},
        "tables": registered,
        "changesets": changesets(result),
        "adapters": annotated,
        "json_columns": json_columns(result),
        "adapter_tables": sorted({item["table"] for item in annotated}),
        "dropped_columns": result["dropped"],
        "renamed_columns": result["renamed"],
    }
