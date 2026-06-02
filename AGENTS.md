<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Agent Guide for cxf-xjc-utils

This file is read by automated agents (security scanners, code analyzers,
AI assistants) operating on this repository.

## Security

Security model: [SECURITY.md](./SECURITY.md) -> the Apache CXF umbrella threat
model at https://github.com/apache/cxf/blob/main/THREAT_MODEL.md

This repository is build-time XJC (XML Schema to Java) tooling and Maven plugins for Apache CXF — build-time tooling, not a runtime service. It is
placed out of the runtime threat model by the Apache CXF umbrella model linked
above; agents should consult that document before reporting issues.
