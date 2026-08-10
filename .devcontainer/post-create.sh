#!/usr/bin/env bash
#
# Runs once, when the dev container is created.
set -euo pipefail

me="$(id -u):$(id -g)"

# Named volumes are created root-owned: without this step Maven, the JetBrains
# backend and gh cannot write into them. The parent directories matter too —
# `mkdir -p ~/.cache/JetBrains` creates ~/.cache itself as root, which would
# then break anything else writing under it.
echo "==> Handing the mounted volumes to $(id -un)"
for dir in .m2 .cache .cache/JetBrains .config .config/gh; do
    sudo mkdir -p "${HOME}/${dir}"
done
sudo chown "${me}" "${HOME}/.cache" "${HOME}/.config"
for dir in .m2 .cache/JetBrains .config/gh; do
    # Skip the recursive walk when the volume is already ours: once populated,
    # ~/.m2 holds tens of thousands of files and this runs on every recreate.
    if [ "$(stat -c %u "${HOME}/${dir}")" != "$(id -u)" ]; then
        sudo chown -R "${me}" "${HOME}/${dir}"
    fi
done

# The workspace is a bind mount, so it carries a host uid. On a Linux host whose
# uid is not 1000 git would otherwise refuse to touch the repository.
git config --global --add safe.directory "$(pwd)"

# Warm the Maven cache. On a multi-module reactor go-offline can trip over
# sibling modules that are not installed yet: this is an optimization, not a
# requirement.
echo "==> Warming the Maven cache"
if ! mvn -B -q -DskipTests dependency:go-offline; then
    echo "    (dependency:go-offline did not complete — not blocking)"
fi

echo "==> Dev container ready."
