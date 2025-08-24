# OpenTelemetry Java Contrib Dependency Management

This module provides dependency management (Bill of Materials) for all OpenTelemetry Java Contrib projects. It centralizes version management for common dependencies used across the contrib modules.

## Purpose

This module serves as a platform/BOM (Bill of Materials) that:

- Defines version constraints for shared dependencies
- Imports upstream BOMs from OpenTelemetry instrumentation and other projects  
- Ensures consistent dependency versions across all contrib modules
- Helps avoid version conflicts and dependency hell

## Usage

This module is automatically applied to all contrib modules through the `otel.java-conventions` Gradle plugin. Individual modules don't need to explicitly depend on this platform.

## Component owners

- [Jack Berg](https://github.com/jack-berg), Splunk
- [Trask Stalnaker](https://github.com/trask), Microsoft

Learn more about component owners in [component_owners.yml](../.github/component_owners.yml).