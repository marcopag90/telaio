# Contributing to Telaio

Thank you for taking the time to contribute! Bug reports, documentation improvements, and code are all welcome.

For anything beyond a small fix, please **open an issue first** to discuss the idea. It avoids wasted work on both
sides and usually leads to a better design.

## Development workflow (Gitflow)

Telaio follows a strict [Gitflow](https://nvie.com/posts/a-successful-git-branching-model/) process:

- **`main`** always reflects the **latest published release** (each release is tagged `vX.Y.Z` and published to Maven
  Central).
- **`development`** is where the **next release** is built.

Pick your base branch according to the kind of change:

| You want to…                                        | Fork / branch from | Branch name        | PR target     |
|-----------------------------------------------------|--------------------|--------------------|---------------|
| Add a feature, improve code or docs, fix a bug      | `development`      | `feature/<name>`   | `development` |
| Fix an **urgent bug in the latest release**         | `main`             | `hotfix/<name>`    | `main`        |

Hotfix pull requests are reserved for defects that affect the published release and cannot wait for the next release
train — the fix ships on its own, without dragging in whatever else is already on `development`. **If in doubt, target
`development`.** After a hotfix PR is merged, the maintainer cuts the actual hotfix release (version bump, `vX.Y.Z`
tag, publication to Maven Central, back-merge into `development`).

Releases themselves are maintainer-only and driven by the gitflow tooling. Two hard rules follow from that:

- **Never bump versions in a PR.** Feature branches keep the plain `-SNAPSHOT` version, and on `main` the POMs carry
  the released version. All version changes are made by the release tooling.
- **Never put `[skip ci]` in a commit message.** It suppresses the whole workflow and leaves the required status check
  stuck in "Pending", making the PR unmergeable. Documentation-only changes are detected automatically and skip the
  build on their own.

## Building and testing

There are two ways to get a working build environment:

### Option A — dev container (nothing to install)

The repository ships a ready-to-use [dev container](.devcontainer/README.md) with JDK 25, Maven, and a real Docker
daemon for the Testcontainers suites. All you need on the host is Docker and an IDE client (IntelliJ IDEA, VS Code, or
the devcontainers CLI) — see [`.devcontainer/README.md`](.devcontainer/README.md) for the exact steps per client.

### Option B — local toolchain

- **JDK 25** is required for the full reactor build, because the reactor includes the `telaio-showcase` demo (the only
  module targeting Java 25). The showcase integration tests also need a **running Docker daemon** (Testcontainers).
- With **JDK 21** you can build everything except the showcase.

Always run Maven **from the repository root** — the `telaio-bom` import resolves from the reactor, so building inside
a module directory fails.

```bash
mvn -B clean verify                     # full reactor — exactly what CI runs (JDK 25 + Docker)
mvn clean install -pl '!telaio-showcase' # library modules only (JDK 21 is enough)
mvn -pl telaio-core clean install       # a single module, still from the root
```

## Before you open a pull request

- **Write tests.** Every new class gets a dedicated JUnit test, and new behavior gets integration tests. Changes
  without test coverage will not be merged.
- Make sure the build is green locally (`mvn -B clean verify`, or the library-only build if your change does not touch
  the showcase).
- **Code and Javadoc are written in English.**
- Match the style and patterns of the surrounding code — the existing module is the style guide.
- Update the documentation under [`docs/`](docs/README.md) (and the README, where relevant) when behavior changes.

## Pull requests

- CI runs automatically on your PR; the `build` check must be green. PRs to `development` must also be up to date with
  the base branch before merging.
- PRs are **squash-merged**, so your PR **title becomes the commit message**. Write it as a
  [Conventional Commit](https://www.conventionalcommits.org/): `type(scope): summary` — for example `fix(jpa): handle
  null embedded ids in delete` or `feat(web): expose count endpoint`. Intermediate commits on the branch can stay
  messy; they disappear at merge time.

## Reporting issues

Use the [GitHub issue tracker](https://github.com/marcopag90/telaio/issues). For bugs, please include:

- the affected module(s) and the Telaio version,
- a minimal reproduction (entity + configuration + the request or call that misbehaves),
- the full error message and stack trace.

Feature requests are welcome through issues as well.

## Security

**Never report security vulnerabilities through public issues.** See [`SECURITY.md`](SECURITY.md) for the private
disclosure process.

## License

Telaio is licensed under the [Apache License, Version 2.0](LICENSE). By submitting a contribution, you agree that it
is licensed under the same terms (Apache-2.0, Section 5) — no CLA required.
