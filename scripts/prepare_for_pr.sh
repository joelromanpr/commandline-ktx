#!/usr/bin/env bash
set -euo pipefail

# Always run from repo root regardless of where this script is invoked
cd "$(dirname "$0")/.."

print_intro() {
  cat <<'EOF'
Prepare for PR: this script applies Spotless formatting by default.

Examples:
  - Entire project (default):
      scripts/prepare_for_pr.sh
  - Entire project (check mode):
      scripts/prepare_for_pr.sh --check
  - Specific module (apply):
      scripts/prepare_for_pr.sh :library
      scripts/prepare_for_pr.sh demo
  - Specific module (check mode):
      scripts/prepare_for_pr.sh :library --check
  - Pass extra Gradle flags:
      scripts/prepare_for_pr.sh -- --stacktrace --continue

EOF
}

usage() {
  print_intro
  echo "Usage:"
  echo "  scripts/prepare_for_pr.sh [<modulePath>] [--check] [-y|--yes] [-- <extra gradle args>]"
  echo
  echo "Notes:"
  echo "  - <modulePath> can be ':library' or 'demo' (nested module paths are supported)."
  echo "  - Default action is 'apply' across the entire project."
  echo "  - Use --check for verification without modifying files."
  exit 0
}

normalize_module_path() {
  local m="${1:-}"
  if [[ -z "${m}" ]]; then
    echo ""
    return
  fi
  if [[ "${m}" == :* ]]; then
    echo "${m}"
  else
    echo ":${m}"
  fi
}

# Parse args
module=""
mode="apply"      # apply | check
auto_yes="false"
declare -a extra_args=()

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
fi

# Split args at '--' to forward the rest to Gradle
forward=false
for arg in "$@"; do
  if [[ "${forward}" == "true" ]]; then
    extra_args+=("$arg")
    continue
  fi
  case "${arg}" in
    --)
      forward=true
      ;;
    --check)
      mode="check"
      ;;
    -y|--yes)
      auto_yes="true"
      ;;
    -*)
      # Unknown flags before '--' -> show usage
      usage
      ;;
    *)
      # First non-flag is module path (optional)
      if [[ -z "${module}" ]]; then
        module="${arg}"
      else
        usage
      fi
      ;;
  esac
done

module="$(normalize_module_path "${module}")"
task="spotlessApply"
[[ "${mode}" == "check" ]] && task="spotlessCheck"

print_intro

# Build the command safely using an array
cmd=(./gradlew --no-daemon)
if [[ -n "${module}" ]]; then
  cmd+=("${module}:${task}")
else
  cmd+=("${task}")
fi
# Append extra args only if present (avoids unbound issues under set -u)
for arg in "${extra_args[@]}"; do
  cmd+=("$arg")
done

# Show what will run
printf "About to run: "
printf "%q " "${cmd[@]}"
printf "\n\n"

# Auto-confirm in CI or with -y/--yes
if [[ "${auto_yes}" != "true" && -z "${CI:-}" ]]; then
  read -r -p "Proceed? [Y/n]: " reply
  reply="${reply:-Y}"
  case "${reply}" in
    [Yy]*) ;;
    *) echo "Aborted."; exit 0 ;;
  esac
fi

# Execute
"${cmd[@]}"

echo
echo "Done. Spotless ${mode} completed."
