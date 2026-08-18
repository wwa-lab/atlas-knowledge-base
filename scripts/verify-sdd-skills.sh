#!/usr/bin/env bash

set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
lock_file="$repo_root/docs/00-context/sdd-skills.lock"
local_skills="$repo_root/.agents/skills"

fail() {
  printf 'SDD skill verification failed: %s\n' "$*" >&2
  exit 1
}

command -v git >/dev/null 2>&1 || fail "git is required"
command -v diff >/dev/null 2>&1 || fail "diff is required"
test -f "$lock_file" || fail "missing lock file: $lock_file"
test -d "$local_skills" || fail "missing local skill directory: $local_skills"

source_repository=
source_branch=
source_subdirectory=
source_commit=

while IFS='=' read -r key value; do
  case "$key" in
    ''|'#'*) ;;
    repository) source_repository=$value ;;
    branch) source_branch=$value ;;
    subdirectory) source_subdirectory=$value ;;
    commit) source_commit=$value ;;
    *) fail "unknown lock key: $key" ;;
  esac
done < "$lock_file"

test "$source_repository" = "https://github.com/wwa-lab/Agentic-SDLC-Control-Tower.git" ||
  fail "unexpected source repository: $source_repository"
test "$source_branch" = "main" || fail "unexpected source branch: $source_branch"
test "$source_subdirectory" = ".claude/skills" ||
  fail "unexpected source subdirectory: $source_subdirectory"
[[ "$source_commit" =~ ^[0-9a-f]{40}$ ]] || fail "invalid source commit: $source_commit"

work_dir=$(mktemp -d /tmp/atlas-sdd-skills.XXXXXX)
cleanup() {
  case "$work_dir" in
    /tmp/atlas-sdd-skills.*) rm -rf -- "$work_dir" ;;
    *) printf 'Refusing to remove unexpected temporary path: %s\n' "$work_dir" >&2 ;;
  esac
}
trap cleanup EXIT

git clone --quiet --depth 1 --branch "$source_branch" \
  "$source_repository" "$work_dir/source" ||
  fail "could not clone the authoritative repository"

actual_commit=$(git -C "$work_dir/source" rev-parse HEAD)
test "$actual_commit" = "$source_commit" ||
  fail "upstream $source_branch moved from $source_commit to $actual_commit; review and re-sync before updating the lock"

upstream_skills="$work_dir/source/$source_subdirectory"
test -d "$upstream_skills" || fail "upstream skill directory is missing"

if ! diff -qr "$upstream_skills" "$local_skills"; then
  fail "local .agents/skills is not an exact mirror of $source_repository/$source_subdirectory@$source_commit"
fi

printf 'PASS: .agents/skills exactly matches %s/%s@%s\n' \
  "$source_repository" "$source_subdirectory" "$source_commit"
