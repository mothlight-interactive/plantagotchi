#!/usr/bin/env bash
# One build number scheme for all workflows (TestFlight, Play internal, production):
# minutes since 2023-11-14 UTC. Always increasing, so Play's versionCode (must grow across all
# tracks) and Apple's build number (must grow within a version) never collide.
set -euo pipefail
echo $(( ($(date -u +%s) - 1700000000) / 60 ))
