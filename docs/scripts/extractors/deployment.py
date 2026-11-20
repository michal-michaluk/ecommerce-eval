"""Image, manifest and runtime configuration facts of the deployment.

Starting point: kustomize base/overlays plus a build file that produces the image. The three
directories below derive from ``sources.k8s`` in ``docs/scripts/config.json``; re-map the
parsing for another manifest toolchain.
"""

from __future__ import annotations

import re

from . import common

BASE = f"{common.K8S}/base"
OVERLAYS = f"{common.K8S}/overlays"
INFRA = f"{common.K8S}/infra"


def _gradle_value(text: str, pattern: str, group: int = 1) -> str | None:
    match = re.search(pattern, text)
    return match.group(group) if match else None


def _gradle_list(text: str, pattern: str) -> list[str]:
    match = re.search(pattern, text)
    if not match:
        return []
    return re.findall(r'"([^"]+)"', match.group(1))


def _line_of(text: str, pattern: str) -> int:
    match = re.search(pattern, text)
    return common.line_at(text, match.start()) if match else 1


def _declared(path: str, key: str) -> str | None:
    """The reference to the line that declares ``key`` in a manifest, or nothing when it declares it not."""
    text = common.read(path)
    for number, line in enumerate(text.splitlines(), start=1):
        if re.match(r"^\s*" + re.escape(key) + r":", line):
            return common.ref(path, number)
    return None


def _environment(container: dict, path: str) -> list[dict]:
    entries = []
    for item in container.get("env") or []:
        if not isinstance(item, dict):
            continue
        source = {"kind": "literal", "value": item.get("value")}
        reference = item.get("valueFrom") or {}
        for kind, key in (("secretKeyRef", "secret"), ("configMapKeyRef", "configmap"),
                          ("fieldRef", "field"), ("resourceFieldRef", "resource")):
            if kind in reference:
                body = reference[kind] or {}
                source = {"kind": key, "name": body.get("name"), "key": body.get("key") or body.get("fieldPath")}
        entries.append({"name": item.get("name"), "source": source,
                        "evidence": [common.ref(path,
                                                common.yaml_value_line(common.read(path),
                                                                       item.get("name") or ""))]})
    return entries


def _probe(container: dict, name: str, path: str) -> dict | None:
    probe = container.get(name)
    if not isinstance(probe, dict):
        return None
    http = probe.get("httpGet") or {}
    return {
        "name": name,
        "path": http.get("path"),
        "port": http.get("port"),
        "initial_delay_seconds": probe.get("initialDelaySeconds"),
        "period_seconds": probe.get("periodSeconds"),
        "timeout_seconds": probe.get("timeoutSeconds"),
        "failure_threshold": probe.get("failureThreshold"),
        "evidence": [common.ref(path, common.yaml_key_line(common.read(path), name))],
    }


def _container(manifest: dict, path: str) -> dict:
    containers = ((manifest.get("spec") or {}).get("template") or {}).get("spec", {}).get("containers") or []
    container = containers[0] if containers else {}
    security = container.get("securityContext") or {}
    resources = container.get("resources") or {}
    ports = container.get("ports") or []
    return {
        "name": container.get("name"),
        "image": container.get("image"),
        "image_pull_policy": container.get("imagePullPolicy"),
        "container_port": (ports[0] or {}).get("containerPort") if ports else None,
        "protocol": (ports[0] or {}).get("protocol") if ports else None,
        "environment": _environment(container, path),
        "probes": [probe for probe in (
            _probe(container, "livenessProbe", path),
            _probe(container, "readinessProbe", path),
            _probe(container, "startupProbe", path),
        ) if probe],
        "resources": {"requests": resources.get("requests") or {}, "limits": resources.get("limits") or {}},
        "security": {key: security.get(key) for key in
                     ("runAsNonRoot", "readOnlyRootFilesystem", "allowPrivilegeEscalation") if key in security},
        "evidence": [reference for reference in (_declared(path, "containers"),
                                                _declared(path, "resources")) if reference],
    }


def _manifest(path: str) -> tuple[dict, str]:
    text = common.read(path)
    return common.parse_yaml(text) or {}, text


def _base() -> dict:
    deployment_path = f"{BASE}/deployment.yaml"
    service_path = f"{BASE}/service.yaml"
    configmap_path = f"{BASE}/configmap.yaml"
    deployment, _ = _manifest(deployment_path)
    service, _ = _manifest(service_path)
    configmap, _ = _manifest(configmap_path)
    ports = ((service.get("spec") or {}).get("ports") or [{}])[0]
    metadata = deployment.get("metadata") or {}
    pod_security = (((deployment.get("spec") or {}).get("template") or {}).get("spec") or {}).get(
        "securityContext") or {}
    return {
        "deployment": {
            "name": metadata.get("name"),
            "replicas": (deployment.get("spec") or {}).get("replicas"),
            "container": _container(deployment, deployment_path),
            "pod_security": pod_security,
            "path": deployment_path,
            "evidence": [common.ref(deployment_path, common.yaml_key_line(common.read(deployment_path), "kind"))],
        },
        "service": {
            "name": (service.get("metadata") or {}).get("name"),
            "type": (service.get("spec") or {}).get("type"),
            "port": ports.get("port"),
            "target_port": ports.get("targetPort"),
            "protocol": ports.get("protocol"),
            "path": service_path,
            "evidence": [common.ref(service_path, common.yaml_key_line(common.read(service_path), "kind"))],
        },
        "configmap": {
            "name": (configmap.get("metadata") or {}).get("name"),
            "keys": sorted((configmap.get("data") or {}).keys()),
            "path": configmap_path,
            "evidence": [reference for reference in
                         (_declared(configmap_path, key) for key in sorted((configmap.get("data") or {})))
                         if reference] or [common.ref(configmap_path,
                                                     common.yaml_key_line(common.read(configmap_path), "data"))],
        },
    }


def _overlays() -> list[dict]:
    overlays = []
    for directory in sorted(path for path in (common.REPO / OVERLAYS).iterdir() if path.is_dir()):
        kustomization_path = f"{OVERLAYS}/{directory.name}/kustomization.yaml"
        kustomization, _ = _manifest(kustomization_path)
        literals = []
        for generator in kustomization.get("configMapGenerator") or []:
            literals.extend(generator.get("literals") or [])
        secrets = []
        for generator in kustomization.get("secretGenerator") or []:
            secrets.extend(item.partition("=")[0] for item in generator.get("literals") or [])
        patch_path = f"{OVERLAYS}/{directory.name}/deployment-patch.yaml"
        patch = {}
        container = None
        if (common.REPO / patch_path).is_file():
            patch, _ = _manifest(patch_path)
            patch_containers = ((((patch.get("spec") or {}).get("template") or {}).get("spec") or {})
                                .get("containers") or [])
            container = _container(patch, patch_path) if patch_containers else None
        files = sorted(item.name for item in directory.iterdir() if item.is_file())
        overlays.append({
            "name": directory.name,
            "namespace": kustomization.get("namespace"),
            "name_prefix": kustomization.get("namePrefix"),
            "resources": kustomization.get("resources") or [],
            "configmap_literals": [item.partition("=")[0] for item in literals],
            "configmap_values": [{"name": item.partition("=")[0], "value": item.partition("=")[2]} for item in literals],
            "secret_keys": sorted(secrets),
            "container": container,
            "files": files,
            "path": kustomization_path,
            "evidence": [common.ref(kustomization_path,
                                     common.yaml_key_line(common.read(kustomization_path), "kind"))],
        })
    return overlays


def _infrastructure() -> list[dict]:
    """The manifests of the objects the service depends on, none of which an overlay composes."""
    entries = []
    for path in sorted((common.REPO / INFRA).glob("*.yaml")):
        relative = common.rel(path)
        text = common.read(relative)
        parsed = common.parse_yaml(text)
        documents = parsed if isinstance(parsed, list) else [parsed]
        objects = [
            {"kind": document.get("kind"), "name": (document.get("metadata") or {}).get("name"),
             "namespace": (document.get("metadata") or {}).get("namespace")}
            for document in documents if isinstance(document, dict)
        ]
        entries.append({"file": path.name, "path": relative, "objects": objects,
                        "evidence": [common.ref(relative, common.yaml_key_line(text, "kind"))]})
    return entries


def _image() -> dict:
    text = common.read(common.BUILD_FILE)
    start = text.index("jib {")
    block = text[start:]
    offset = text.count("\n", 0, start)

    def line(pattern: str) -> int:
        """The line of a pattern inside the ``jib`` block, as a line of the build file."""
        return offset + _line_of(block, pattern)

    environment = []
    for name, value in re.findall(r'"([A-Z_]+)":\s*"([^"]*)"', block):
        environment.append({"name": name, "value": value,
                            "evidence": [common.ref(common.BUILD_FILE, line('"' + name + '":'))]})
    return {
        "base_image": _gradle_value(block, r'from\s*\{\s*image\s*=\s*"([^"]+)"'),
        "name": _gradle_value(block, r'to\s*\{\s*image\s*=\s*"([^"]+)"'),
        "tags": _gradle_list(block, r"tags\s*=\s*\[([^\]]*)\]"),
        "main_class": _gradle_value(block, r'mainClass\s*=\s*"([^"]+)"'),
        "ports": _gradle_list(block, r"\bports\s*=\s*\[([^\]]*)\]"),
        "jvm_flags": _gradle_list(block, r"jvmFlags\s*=\s*\[([^\]]*)\]"),
        "environment": environment,
        "agent_version": _gradle_value(text, r'otelAgentVersion\s*=\s*"([^"]+)"'),
        "agent_jar": _gradle_value(text, r'otelAgentDir\w*\.map\s*\{\s*it\.file\("([^"]+)"\)'),
        "path": common.BUILD_FILE,
        "evidence": [common.ref(common.BUILD_FILE, line(r"jib \{")),
                     common.ref(common.BUILD_FILE, line(r'image\s*=\s*"[^"]+"')),
                     common.ref(common.BUILD_FILE, line(r"\btags\s*=")),
                     common.ref(common.BUILD_FILE, line(r"\bmainClass\s*=")),
                     common.ref(common.BUILD_FILE, line(r"\bports\s*=")),
                     common.ref(common.BUILD_FILE, line(r"\bjvmFlags\s*="))],
    }


def extract() -> dict:
    base = _base()
    overlays = _overlays()
    profiles = []
    for entry in base["deployment"]["container"]["environment"]:
        if entry["name"] and "PROFILES" in entry["name"]:
            profiles.append({"name": entry["source"].get("value"), "overlay": None,
                             "evidence": entry["evidence"]})
    for overlay in overlays:
        for entry in (overlay["container"] or {}).get("environment") or []:
            if entry["name"] and "PROFILES" in entry["name"]:
                profiles.append({"name": entry["source"].get("value"), "overlay": overlay["name"],
                                 "evidence": entry["evidence"]})
    return {
        "image": _image(),
        "base": base,
        "overlays": overlays,
        "infrastructure": _infrastructure(),
        "profiles": profiles,
        "evidence": base["deployment"]["evidence"] + base["service"]["evidence"],
    }
