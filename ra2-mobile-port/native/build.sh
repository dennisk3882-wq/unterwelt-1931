#!/usr/bin/env bash
set -euo pipefail
source_dir=$(cd "$(dirname "$0")" && pwd)
output_dir=$1
compiler=$2
shift 2
mkdir -p "$output_dir"
"$compiler" -shared -fPIC -O2 -D_FILE_OFFSET_BITS=64 -DHAVE_FSEEKO -DHAVE_FTELLO \
 -I"$source_dir/mspack" "$@" "$source_dir/ra2cab.c" "$source_dir/mspack/system.c" \
 "$source_dir/mspack/cabd.c" "$source_dir/mspack/mszipd.c" "$source_dir/mspack/lzxd.c" "$source_dir/mspack/qtmd.c" \
 -Wl,-z,max-page-size=16384 -o "$output_dir/libra2cab.so"
