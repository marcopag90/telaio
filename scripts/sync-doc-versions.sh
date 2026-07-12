#!/usr/bin/env bash
#
# Syncs the Telaio version shown in documentation code snippets (README.md and docs/**/*.md)
# with the project version. Intended to run on a release/* or hotfix/* branch right after the
# gitflow version bump, next to the CHANGELOG update, as its own dedicated commit
#
# Usage:
#   scripts/sync-doc-versions.sh [--check] [--version X.Y.Z]
#
#   --version X.Y.Z  use this version instead of resolving it from the pom (mvn help:evaluate)
#   --check          verify only: exit 1 listing stale occurrences, write nothing
#
# Rewritten inside the markdown files (pattern-based, idempotent):
#   - <version>...</version> immediately following a telaio-* <artifactId> — other <version>
#     elements in the snippets (third-party deps, Spring Boot examples) and ${...} property
#     references are left untouched
#   - <telaio.version>...</telaio.version> property examples
set -euo pipefail

cd "$(dirname "$0")/.."

CHECK=0
VERSION=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --check) CHECK=1; shift ;;
        --version) VERSION="${2:?--version requires a value}"; shift 2 ;;
        *) echo "Unknown argument: $1 (usage: $0 [--check] [--version X.Y.Z])" >&2; exit 2 ;;
    esac
done

if [[ -z "$VERSION" ]]; then
    # tail -1 skips stray [WARNING]/download lines; tr strips the CRLF mvn.cmd emits on Git Bash
    VERSION="$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout | tail -1 | tr -d '\r')"
fi

if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "ERROR: '$VERSION' is not a release version (X.Y.Z)." >&2
    echo "Run this script on a release/* or hotfix/* branch (after the gitflow version bump)," >&2
    echo "or pass an explicit release version with --version X.Y.Z." >&2
    exit 1
fi
export TELAIO_DOC_VERSION="$VERSION"

FILES=()
[[ -f README.md ]] && FILES+=(README.md)
while IFS= read -r f; do
    FILES+=("$f")
done < <(find docs -type f -name '*.md' 2>/dev/null | sort)

report_stale() {
    # Prints one line per occurrence whose version differs from TELAIO_DOC_VERSION.
    perl -0777 -ne '
        while (/<artifactId>(telaio-[a-z0-9-]+)<\/artifactId>\s*\n\s*<version>([^<]+)<\/version>/g) {
            # ${...} values reference a property (rewritten via <telaio.version>), not a literal
            print "$1 -> $2\n" if $2 !~ /^\$\{/ && $2 ne $ENV{TELAIO_DOC_VERSION};
        }
        while (/<telaio\.version>([^<]+)<\/telaio\.version>/g) {
            print "telaio.version -> $1\n" if $1 ne $ENV{TELAIO_DOC_VERSION};
        }
    ' "$1"
}

if [[ "$CHECK" -eq 1 ]]; then
    STALE=0
    for f in "${FILES[@]}"; do
        hits="$(report_stale "$f")"
        if [[ -n "$hits" ]]; then
            STALE=1
            echo "STALE: $f"
            sed 's/^/    /' <<<"$hits"
        fi
    done
    if [[ "$STALE" -eq 1 ]]; then
        echo "Documented Telaio version is not aligned to $VERSION. Run scripts/sync-doc-versions.sh to fix." >&2
        exit 1
    fi
    echo "OK: documented Telaio version is aligned to $VERSION."
    exit 0
fi

for f in "${FILES[@]}"; do
    perl -0777 -pi -e '
        s{(<artifactId>telaio-[a-z0-9-]+</artifactId>\s*\n\s*<version>)(?!\$\{)[^<]+(</version>)}{$1$ENV{TELAIO_DOC_VERSION}$2}g;
        s{(<telaio\.version>)[^<]+(</telaio\.version>)}{$1$ENV{TELAIO_DOC_VERSION}$2}g;
    ' "$f"
done
echo "Documented Telaio version set to $VERSION."
git --no-pager diff --stat -- "${FILES[@]}" || true
