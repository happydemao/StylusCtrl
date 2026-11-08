#!/bin/sh
# Same two daemons as build.sh, but through zig cc. Zig runs on macOS and on
# Linux, so this is the path when the musl.cc cross compilers are not unpacked.
#   brew install zig        (or set STYLUSCTRL_ZIG to the binary)
# Writes the same out/stylusctld and out/stylusctld32 as build.sh, both
# statically linked against musl.
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
zig=${STYLUSCTRL_ZIG:-zig}
mkdir -p "$root/out"

"$zig" cc -target aarch64-linux-musl -std=c11 -static -Os -Wall -Wextra \
    -o "$root/out/stylusctld" "$root/tools/stylusctld.c" -lm
echo "$root/out/stylusctld"

"$zig" cc -target arm-linux-musleabihf -std=c11 -static -Os -Wall -Wextra \
    -o "$root/out/stylusctld32" "$root/tools/stylusctld.c" -lm
echo "$root/out/stylusctld32"
