# Security Policy

## Reporting a Vulnerability

For how to report a security problem, see [the ASF security process](https://www.apache.org/security/).
Apache CXF's existing security advisories are published at <https://cxf.apache.org/security-advisories.html>.
Please do **not** open public GitHub issues or pull requests for security reports.

## Threat Model

`apache/cxf-xjc-utils` is build-time XJC (XML Schema to Java) tooling and Maven plugins for Apache CXF. It runs at build time and is not a runtime service, so it has no runtime threat
model of its own; its security context is covered by the **Apache CXF umbrella threat model**, which places
build-time tooling outside the runtime model:

  https://github.com/apache/cxf/blob/main/THREAT_MODEL.md

Reporters and triagers should consult that document (in particular its scope / out-of-scope sections)
alongside this policy.
