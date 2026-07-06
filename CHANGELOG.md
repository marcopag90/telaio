# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2026-07-06

First public release, available on Maven Central under the `com.paganbit` group id.

### Added

- Unified, persistence-agnostic Data Access Layer (`Dal` / `AbstractDal`) with dynamic
  property-map CRUD, validation, property merging, transactional hooks and a
  `@DalService` registry (`telaio-core`, `telaio-introspection`).
- Dynamic REST API (`/dal/v1/{dalName}`) with Turkraft Spring Filter queries, Spring Data
  pagination and RFC 9457 `ProblemDetail` error responses (`telaio-web`).
- Per-DAL exposure control: `@DalService(internal = true)` hides a DAL from every remote
  boundary, `@DalService(operations = {...})` restricts the exposed CRUD operations
  (`404`/`405` at the web boundary, omitted from OpenAPI).
- CRUD-level authorization (`DalAuthAdapter`) and field-level RBAC
  (`PropertyBasedDalRbacAdapter`, `JsonViewDalRbacAdapter`) on top of Spring Security
  (`telaio-security`).
- Opt-in auditing of DAL operations with logfmt / JSON Lines output on a dedicated logger
  category, MDC correlation and granular outcomes (SUCCESS / DENIED / VALIDATION /
  NOT_FOUND / CONFLICT / ERROR) (`telaio-audit`).
- Usage and latency metrics per DAL and operation with time-bucketed aggregation,
  in-memory and multi-vendor JDBC stores (PostgreSQL, MySQL, MariaDB, Oracle, SQL Server),
  an optional Micrometer path and the `telaiometrics` actuator endpoint (`telaio-metrics`).
  Client faults (validation, not-found, conflicts) are counted separately from service
  errors (`client_error_count`).
- Concrete per-DAL OpenAPI documentation replacing the generic templated operations
  (`telaio-openapi`).
- JPA/Hibernate backend (`JpaDal`) with generic-aware setter injection and
  filter-to-`Specification` conversion (`telaio-jpa`).
- `telaio-bom` Bill of Materials aligning all library modules and integrated third
  parties.
- Concurrent modification of a `@Version`-ed entity maps to `409 Conflict`.
- GitFlow release tooling and Maven Central publishing: signed artifacts with sources and
  javadoc jars, uploaded to the Central Portal from CI on release tags.

### Fixed

- `DELETE` enforces the DAL's `defaultFilter` and runs its visibility check inside the
  delete transaction (TOCTOU hardening); deleting an entity outside the filter now
  returns `404` (previously `204`).

[unreleased]: https://github.com/marcopag90/telaio/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/marcopag90/telaio/releases/tag/v1.0.0
