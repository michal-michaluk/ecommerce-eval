"""The authorization decision declared by the security filter chain.

Starting point: one Spring-style filter chain whose rules are ``requestMatchers(pathPattern(...))``
followed by a decision, with a fallback rule. The matcher may name its HTTP method, and the scope a
decision grants is a constant of the configuration class, so the value the token must carry is
resolved from the declaration rather than copied. Keep the output shape: a rule per matcher with the
line that declares it, plus the fallback, so an operation can cite its own authorization decision.
"""

from __future__ import annotations

import re

from . import common

METHOD_ANNOTATIONS = ("PreAuthorize", "PostAuthorize", "Secured", "RolesAllowed", "PreFilter", "PostFilter")

RULE = re.compile(
    r"requestMatchers\(\s*pathPattern\(\s*(?:(?P<method>[A-Z]+)\s*,\s*)?\"(?P<path>[^\"]+)\"\s*\)\s*\)"
    r"\s*\.(?P<decision>\w+)\((?P<arguments>[^)]*)\)",
    re.S,
)
FALLBACK = re.compile(r"anyRequest\(\)\s*\.(\w+)\(\)")
DECLARATION = re.compile(r"SecurityFilterChain\s+\w+\s*\(")
CONSTANT = re.compile(r'static\s+final\s+String\s+(\w+)\s*=\s*"([^"]+)"\s*;')


def _constants(text: str) -> dict[str, str]:
    """The String constants the decision expressions name, so a scope is cited by its value."""
    return {name: value for name, value in CONSTANT.findall(text)}


def _decision(kind: str, arguments: str, constants: dict[str, str]) -> tuple[str, list[str]]:
    """The decision as the chain spells it, with every constant resolved to the value it holds."""
    names = [name.strip() for name in arguments.split(",") if name.strip()]
    resolved = [constants.get(name, name) for name in names]
    return f"{kind}({', '.join(resolved)})" if resolved else kind, resolved


def _rules(text: str) -> list[dict]:
    constants = _constants(text)
    rules = []
    for match in RULE.finditer(text):
        decision, authorities = _decision(match.group("decision"), match.group("arguments"), constants)
        rules.append({
            "method": match.group("method"),
            "matcher": match.group("path"),
            "decision": decision,
            "authorities": authorities,
            "line": common.line_at(text, match.start("path")),
        })
    return rules


def extract() -> dict:
    text = common.read(common.APP_FRAMEWORK_CONFIG)
    rules = _rules(text)
    fallback_match = FALLBACK.search(text)
    fallback = (
        {"matcher": None, "decision": fallback_match.group(1),
         "line": common.line_at(text, fallback_match.start())}
        if fallback_match else None
    )
    mechanism_match = re.search(
        r"\.(oauth2ResourceServer)\(\s*(\w+)\s*->\s*\2\s*\.\s*(\w+)\(", text
    )
    mechanism = {
        "chain": mechanism_match.group(1) if mechanism_match else None,
        "scheme": mechanism_match.group(3) if mechanism_match else None,
        "line": common.line_at(text, mechanism_match.start()) if mechanism_match else None,
    }
    evidence = [common.ref(common.APP_FRAMEWORK_CONFIG, rule["line"]) for rule in rules]
    if fallback:
        evidence.append(common.ref(common.APP_FRAMEWORK_CONFIG, fallback["line"]))
    declaration = DECLARATION.search(text)
    if declaration is None:
        raise SystemExit(f"{common.APP_FRAMEWORK_CONFIG}: declares no SecurityFilterChain")
    return {
        "filter_chain": {
            "path": common.APP_FRAMEWORK_CONFIG,
            "line": common.line_at(text, declaration.start()),
            "rules": rules,
            "fallback": fallback,
            "evidence": evidence,
        },
        "authentication": {
            "mechanism": mechanism,
            "issuer": _issuer(),
            "evidence": ([common.ref(common.APP_FRAMEWORK_CONFIG, mechanism["line"])]
                         if mechanism["line"] else []),
        },
        "method_level_authorization": _method_level(),
        "evidence": evidence,
    }


def _issuer() -> dict:
    text = common.read(common.APP_CONFIG_FILE)
    match = re.search(r"issuer-uri:\s*(\S+)", text)
    if not match:
        return {"expression": None, "line": None}
    expression = match.group(1)
    placeholder = re.fullmatch(r"\$\{(\w+):([^}]*)\}", expression)
    return {
        "expression": expression,
        "variable": placeholder.group(1) if placeholder else None,
        "default": placeholder.group(2) if placeholder else None,
        "path": common.APP_CONFIG_FILE,
        "line": common.line_at(text, match.start()),
        "evidence": [common.ref(common.APP_CONFIG_FILE, common.line_at(text, match.start()))],
    }


def _method_level() -> dict:
    found = []
    for path in common.java_files():
        text = path.read_text(encoding="utf-8")
        for name in METHOD_ANNOTATIONS:
            for match in re.finditer("@" + name, text):
                found.append({"annotation": name, "path": common.rel(path),
                              "line": common.line_at(text, match.start())})
    found.sort(key=lambda item: (item["path"], item["line"]))
    return {
        "present": bool(found),
        "annotations": found,
        "evidence": [common.ref(item["path"], item["line"]) for item in found],
    }
