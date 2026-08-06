#!/bin/sh

# Regenerate Pigeon bindings and verify that the tracked copies are current.
#
# The generated Dart bindings are formatted before comparison so the tracked
# copy can stay freshness-safe under repo-wide `dart format` checks. The
# working tree is restored even when generation or comparison fails.

set -eu

SCRIPT_DIR=$(unset CDPATH; cd "$(dirname "$0")" && pwd -P)
REPO_ROOT=$(unset CDPATH; cd "$SCRIPT_DIR/.." && pwd -P)

die() {
  printf '%s\n' "check_pigeon.sh: $*" >&2
  exit 1
}

cd "$REPO_ROOT"

INPUT='pigeons/messages.dart'
GENERATED_FILES='lib/pigeon_impl_api.dart
android/src/main/kotlin/com/codenameakshay/async_wallpaper/PigeonApi.kt
ios/Classes/PigeonApi.g.swift'
DART_GENERATED_FILE='lib/pigeon_impl_api.dart'

[ -f "$INPUT" ] || die "missing Pigeon input: $INPUT"
command -v git >/dev/null 2>&1 || die 'git is required to verify tracked outputs'
[ -f '.dart_tool/package_config.json' ] || die "dependencies are not installed; run 'flutter pub get' from $REPO_ROOT first"

if [ -f '.fvmrc' ] && command -v fvm >/dev/null 2>&1; then
  USE_FVM=1
  DART_LABEL='fvm dart'
  fvm dart --version >/dev/null 2>&1 || \
    die "FVM could not run the Dart SDK pinned by .fvmrc; run 'fvm install' first"
else
  USE_FVM=0
  DART_LABEL='dart'
  command -v dart >/dev/null 2>&1 || \
    die 'dart is required; install Flutter and make its Dart SDK available'
fi

run_dart() {
  if [ "$USE_FVM" -eq 1 ]; then
    fvm dart "$@"
  else
    dart "$@"
  fi
}

for generated_file in $GENERATED_FILES; do
  [ -f "$generated_file" ] || die "missing tracked Pigeon output: $generated_file"
  git ls-files --error-unmatch "$generated_file" >/dev/null 2>&1 || \
    die "Pigeon output is not tracked: $generated_file"
done

BACKUP_DIR=$(mktemp -d "${TMPDIR:-/tmp}/async-wallpaper-pigeon.XXXXXX") || \
  die 'could not create a temporary directory'
RESTORE_FILES=0

copy_to_temp() {
  source_file=$1
  destination_file=$2
  mkdir -p "$(dirname "$destination_file")"
  cp -p "$source_file" "$destination_file"
}

cleanup() {
  status=$?
  trap - 0 1 2 3 15

  if [ "$RESTORE_FILES" -eq 1 ]; then
    for generated_file in $GENERATED_FILES; do
      backup_file="$BACKUP_DIR/original/$generated_file"
      if [ -f "$backup_file" ] && ! cp -p "$backup_file" "$generated_file"; then
        printf '%s\n' "check_pigeon.sh: could not restore $generated_file" >&2
        status=1
      fi
    done
  fi

  rm -rf "$BACKUP_DIR"
  exit "$status"
}

trap cleanup 0
trap 'exit 1' 1 2 3 15

for generated_file in $GENERATED_FILES; do
  copy_to_temp "$generated_file" "$BACKUP_DIR/original/$generated_file"
done
RESTORE_FILES=1

if ! run_dart run pigeon --input "$INPUT"; then
  die "Pigeon generation failed; the original tracked outputs will be restored"
fi

run_dart format --output=write "$DART_GENERATED_FILE" >/dev/null

for generated_file in $GENERATED_FILES; do
  copy_to_temp "$BACKUP_DIR/original/$generated_file" \
    "$BACKUP_DIR/expected/$generated_file"
  copy_to_temp "$generated_file" "$BACKUP_DIR/actual/$generated_file"
done

has_drift=0
for generated_file in $GENERATED_FILES; do
  expected_file="$BACKUP_DIR/expected/$generated_file"
  actual_file="$BACKUP_DIR/actual/$generated_file"

  if ! cmp -s "$expected_file" "$actual_file"; then
    printf '%s\n' "check_pigeon.sh: stale generated output: $generated_file" >&2
    diff -u "$expected_file" "$actual_file" >&2 || :
    has_drift=1
  fi
done

if [ "$has_drift" -ne 0 ]; then
  die "regenerate bindings with '$DART_LABEL run pigeon --input $INPUT' and commit the resulting tracked outputs"
fi

printf '%s\n' 'Pigeon-generated bindings are up to date.'
