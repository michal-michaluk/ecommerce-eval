## 1 What this manual is for, and what it cannot do

This manual is for the person who has to keep the service running: it states where the service runs, what
configures it, what to change and how, what it exports to be watched, and what each failure looks like from
outside. It is written from the manifests and the code, and it says so wherever the repository does not
answer a question an on-call reader would ask.

<!-- evidence: services/shopping/k8s/base/deployment.yaml:1-25, services/shopping/k8s/kustomization.yaml:1-5 -->

### 1.1 The honest limit of this manual

There is no alerting rule, no dashboard, no service-level objective and no on-call rotation anywhere in this
repository. This manual therefore cannot start with "when alert X fires, do Y", because no X exists. It
starts from what an operator can observe — a probe, a log line, a status code — and from the failures the
code is known to produce. Those absences are entries in chapter 12, not sentences in this chapter.

<!-- evidence: services/shopping/k8s/base/deployment.yaml:121-144, services/shopping/src/main/resources/application.yml:52-57 -->

### 1.2 The shape of the deployment in one paragraph

One Deployment, one replica, one container, one ClusterIP Service on port 80 in front of container port
8080, one ConfigMap and one Secret, three probes, and one mounted file that carries the offer seed. Two
overlays patch it: `dev` for a developer's machine, `k3d` for the shared local cluster, which is the one that
also declares the telemetry and the Ingress.

<!-- evidence: services/shopping/k8s/base/deployment.yaml:8-8, services/shopping/k8s/base/service.yaml:7-14, services/shopping/k8s/overlays/k3d/kustomization.yaml:14-32 -->
