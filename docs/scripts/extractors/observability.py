"""Actuator exposure, metrics export, log levels and the telemetry agent wiring.

Starting point: this reads actuator keys, a metrics registry and an OpenTelemetry agent. Point
``observability.collector_manifest`` in ``docs/scripts/config.json`` at the collector manifest
and re-map the keys for another stack.
"""

from __future__ import annotations

import re

from . import common

COLLECTOR = common.config.DATA["observability"]["collector_manifest"]


def _line(text: str, needle: str) -> int:
    """The first line that contains ``needle``, so a claim about the collector cites the deployment."""
    for number, line in enumerate(text.splitlines(), start=1):
        if needle in line:
            return number
    return 1


def _collector() -> dict:
    """The collector the deployment ships: its pipeline, its exporters and where the files land."""
    if not (common.REPO / COLLECTOR).is_file():
        return {"present": False, "evidence": []}
    text = common.read(COLLECTOR)
    parsed = common.parse_yaml(text)
    documents = parsed if isinstance(parsed, list) else [parsed]
    configuration = {}
    deployment = {}
    service = {}
    for document in documents:
        if not isinstance(document, dict):
            continue
        if document.get("kind") == "ConfigMap":
            configuration = common.parse_yaml(
                ((document.get("data") or {}).get("config.yaml")) or "") or {}
        elif document.get("kind") == "Deployment":
            deployment = document
        elif document.get("kind") == "Service":
            service = document
    spec = ((deployment.get("spec") or {}).get("template") or {}).get("spec") or {}
    container = (spec.get("containers") or [{}])[0]
    volumes = spec.get("volumes") or []
    receivers = []
    for name, body in (configuration.get("receivers") or {}).items():
        for protocol, settings in (body.get("protocols") or {}).items():
            receivers.append({"name": name, "protocol": protocol,
                              "endpoint": (settings or {}).get("endpoint")})
    exporters = [
        {"name": name, "path": body.get("path"),
         "max_megabytes": (body.get("rotation") or {}).get("max_megabytes"),
         "max_days": (body.get("rotation") or {}).get("max_days")}
        for name, body in (configuration.get("exporters") or {}).items()
    ]
    pipelines = [
        {"name": name, "receivers": body.get("receivers") or [], "exporters": body.get("exporters") or []}
        for name, body in ((configuration.get("service") or {}).get("pipelines") or {}).items()
    ]
    return {
        "present": True,
        "config_map": (documents[0].get("metadata") or {}).get("name") if documents else None,
        "receivers": receivers,
        "exporters": exporters,
        "pipelines": pipelines,
        "service": {"name": (service.get("metadata") or {}).get("name"),
                    "ports": [port.get("port") for port in ((service.get("spec") or {}).get("ports") or [])]},
        "deployment": {
            "name": (deployment.get("metadata") or {}).get("name"),
            "replicas": (deployment.get("spec") or {}).get("replicas"),
            "image": container.get("image"),
            "volumes": [{"name": volume.get("name"),
                         "kind": "emptyDir" if "emptyDir" in volume else "configMap" if "configMap" in volume else "other"}
                        for volume in volumes],
            "mounts": [mount.get("mountPath") for mount in (container.get("volumeMounts") or [])],
        },
        "evidence": [common.ref(COLLECTOR, _line(text, "exporters:")),
                     common.ref(COLLECTOR, _line(text, "pipelines:")),
                     common.ref(COLLECTOR, _line(text, "emptyDir: {}")),
                     common.ref(COLLECTOR, _line(text, "image: otel/"))],
    }


def _evidence(key: str) -> list[str]:
    text = common.read(common.APP_CONFIG_FILE)
    return [common.ref(common.APP_CONFIG_FILE, common.yaml_key_line(text, key))]


def _declared_evidence(key: str) -> list[str]:
    """The line that declares ``key``, or nothing: a key the configuration does not declare has no line."""
    text = common.read(common.APP_CONFIG_FILE)
    for number, line in enumerate(text.splitlines(), start=1):
        if re.match(r"^\s*" + re.escape(key) + r":", line):
            return [common.ref(common.APP_CONFIG_FILE, number)]
    return []


def _logger_evidence(logger: str) -> list[str]:
    """The line that sets this logger's level, not the line that opens the whole block."""
    text = common.read(common.APP_CONFIG_FILE)
    for number, line in enumerate(text.splitlines(), start=1):
        if line.strip().startswith(logger + ":"):
            return [common.ref(common.APP_CONFIG_FILE, number)]
    return _evidence("logging.level")


def extract_standalone() -> dict:
    """Build the deployment dataset this module depends on, for a per-dataset run."""
    from . import deployment as deployment_extractor

    return extract(deployment_extractor.extract())


def extract(deployment: dict) -> dict:
    text = common.read(common.APP_CONFIG_FILE)
    configuration = common.parse_yaml(text) or {}
    management = configuration.get("management") or {}
    log_levels = configuration.get("logging.level") or {}
    exposure = management.get("endpoints.web.exposure.include")
    declared_by = []
    telemetry = []
    for overlay in deployment["overlays"]:
        entries = [
            entry for entry in ((overlay["container"] or {}).get("environment") or [])
            if (entry["name"] or "").startswith(("OTEL_", "JAVA_TOOL_OPTIONS"))
        ]
        if not entries:
            continue
        declared_by.append(overlay["name"])
        telemetry.extend({"name": entry["name"], "value": entry["source"].get("value"),
                          "overlay": overlay["name"], "evidence": entry["evidence"]}
                         for entry in entries)
    return {
        "actuator": {
            "exposure": [item.strip() for item in exposure.split(",")] if exposure else [],
            "endpoint_options": [
                {"endpoint": key.removeprefix("endpoint.").removesuffix(".enabled"),
                 "enabled": value, "evidence": _evidence("management." + key)}
                for key, value in management.items()
                if key.startswith("endpoint.") and key.endswith(".enabled")
            ],
            "evidence": _evidence("endpoints.web.exposure.include"),
        },
        "metrics": {
            "prometheus_enabled": management.get("metrics.export.prometheus.enabled"),
            "evidence": _evidence("metrics.export.prometheus.enabled"),
        },
        "documentation": {
            "show_actuator": (configuration.get("springdoc") or {}).get("show-actuator"),
            "evidence": _declared_evidence("show-actuator"),
        },
        "agent": {
            "image_environment": deployment["image"]["environment"],
            "version": deployment["image"]["agent_version"],
            "jar": deployment["image"]["agent_jar"],
            "environment": telemetry,
            "declared_by": sorted(declared_by),
            "evidence": deployment["image"]["evidence"]
            + [reference for entry in telemetry for reference in entry["evidence"]],
        },
        "log_levels": [
            {"logger": logger, "level": level, "evidence": _logger_evidence(logger)}
            for logger, level in log_levels.items()
        ],
        "profiles": deployment["profiles"],
        "collector": _collector(),
        "evidence": _evidence("management"),
    }
