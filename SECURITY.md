# Security Policy

## Supported versions

Security fixes are provided for the **latest released version** (the `main` branch, matching the newest `vX.Y.Z` tag
on Maven Central). Older releases are not patched — please upgrade to the latest version.

## Reporting a vulnerability

**Do not open a public issue for security vulnerabilities.**

Report it privately through GitHub's vulnerability reporting:
[**Report a vulnerability**](https://github.com/marcopag90/telaio/security/advisories/new)
(repository → *Security* tab → *Report a vulnerability*).

If you cannot use GitHub, email [marcopag90@gmail.com](mailto:marcopag90@gmail.com) instead.

Please include the affected module(s) and version, a description of the issue and its impact, and — if possible — a
minimal reproduction.

## What to expect

- An acknowledgment within a few days.
- An assessment of the report and, for confirmed vulnerabilities, a fix shipped as a **hotfix release** from the
  latest version, published to Maven Central.
- Credit in the advisory, unless you prefer to stay anonymous.

> Looking for the documentation of Telaio's *security features* (authorization, RBAC, exposure control)? That lives in
> the [security guide](docs/security-guide.md) — this file is only about reporting vulnerabilities.
