#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if ROOT_GIT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null)"; then
  ROOT_DETECTED="$(cd "$ROOT_GIT" && pwd)"
else
  ROOT_DETECTED="$(cd "$SCRIPT_DIR/../../.." && pwd)"
fi
WRITE_FILE=""
[[ "${1:-}" != "--write" ]] || {
  [[ -n "${2:-}" ]] || { echo "Usage: bash $0 [--write platform.env]" >&2; exit 2; }
  WRITE_FILE="$2"
}

normalize_path() {
  local value="$1"
  [[ -n "$value" ]] || { printf '\n'; return 0; }
  cygpath -u "$value" 2>/dev/null || printf '%s\n' "$value"
}

declare -a DETECT_DRIVE_ROOTS=()
if [[ -n "${DETECT_DRIVES:-}" ]]; then
  read -r -a DETECT_DRIVE_ROOTS <<<"$DETECT_DRIVES"
else
  ROOT_DRIVE="/$(cut -d/ -f2 <<<"$ROOT_DETECTED")"
  [[ -d "$ROOT_DRIVE" ]] && DETECT_DRIVE_ROOTS+=("$ROOT_DRIVE")
  for letter in {c..z}; do
    [[ -d "/$letter" && "/$letter" != "$ROOT_DRIVE" ]] &&
      DETECT_DRIVE_ROOTS+=("/$letter")
  done
fi
DETECT_MAX_DEPTH="${DETECT_MAX_DEPTH:-9}"

find_on_path() {
  local candidate name
  for name in "$@"; do
    candidate="$(command -v "$name" 2>/dev/null || true)"
    [[ -n "$candidate" && -f "$candidate" ]] && {
      normalize_path "$candidate"
      return 0
    }
  done
  return 0
}

find_with_where() {
  local candidate name
  command -v where.exe >/dev/null 2>&1 || return 0
  for name in "$@"; do
    candidate="$(where.exe "$name" 2>/dev/null | tr -d '\r' | head -1 || true)"
    [[ -n "$candidate" && -f "$(normalize_path "$candidate")" ]] && {
      normalize_path "$candidate"
      return 0
    }
  done
  return 0
}

find_on_drives() {
  local drive name candidate
  for drive in "${DETECT_DRIVE_ROOTS[@]}"; do
    for name in "$@"; do
      candidate="$(find "$drive" -maxdepth "$DETECT_MAX_DEPTH" -type f \
        -iname "$name" -print -quit 2>/dev/null || true)"
      [[ -n "$candidate" ]] && {
        normalize_path "$candidate"
        return 0
      }
    done
  done
  return 0
}

find_from_home() {
  local home="$1"; shift
  local relative
  [[ -n "$home" ]] || return 0
  home="$(normalize_path "$home")"
  for relative in "$@"; do
    [[ -f "$home/$relative" ]] && {
      printf '%s\n' "$home/$relative"
      return 0
    }
  done
  return 0
}

discover_tool() {
  local label="$1" configured_home="$2"; shift 2
  local home_relatives="$1"; shift
  local candidate=""
  local -a relatives=()
  read -r -a relatives <<<"$home_relatives"
  candidate="$(find_from_home "$configured_home" "${relatives[@]}")"
  [[ -n "$candidate" ]] || candidate="$(find_on_path "$@")"
  [[ -n "$candidate" ]] || candidate="$(find_with_where "$@")"
  if [[ -z "$candidate" ]]; then
    echo "[INFO] Recherche générique de $label sur les lecteurs disponibles..." >&2
    candidate="$(find_on_drives "$@")"
  fi
  printf '%s\n' "$candidate"
}

PSQL_FOUND="$(discover_tool PostgreSQL "${POSTGRES_HOME:-}" \
  'bin/psql.exe bin/psql' psql.exe psql)"
JAVA_FOUND="$(discover_tool Java "${JAVA_HOME_DIR:-${JAVA_HOME:-}}" \
  'bin/java.exe bin/java' java.exe java)"
MAVEN_FOUND="$(discover_tool Maven "${MAVEN_HOME:-}" \
  'bin/mvn.cmd bin/mvn' mvn.cmd mvn)"
NODE_FOUND="$(discover_tool Node.js "${NODE_HOME:-}" \
  'npm.cmd bin/npm.cmd bin/npm npm' npm.cmd npm)"

home_from_executable() {
  local executable="$1" parent
  [[ -n "$executable" ]] || { printf '\n'; return 0; }
  parent="$(dirname "$executable")"
  [[ "$(basename "$parent")" != "bin" ]] || parent="$(dirname "$parent")"
  printf '%s\n' "$parent"
}

POSTGRES_HOME_DETECTED="$(home_from_executable "$PSQL_FOUND")"
JAVA_HOME_DETECTED="$(home_from_executable "$JAVA_FOUND")"
MAVEN_HOME_DETECTED="$(home_from_executable "$MAVEN_FOUND")"
NODE_HOME_DETECTED="$(home_from_executable "$NODE_FOUND")"
JAVA_VERSION="non détectée"
[[ -z "$JAVA_FOUND" ]] || JAVA_VERSION="$("$JAVA_FOUND" -version 2>&1 | head -1)"

env_line() {
  local key="$1" value="$2" escaped
  printf -v escaped '%q' "$value"
  printf '%s=%s' "$key" "$escaped"
}

declare -a ENV_LINES=(
  "$(env_line ROOT "$ROOT_DETECTED")"
  "$(env_line POSTGRES_HOME "$POSTGRES_HOME_DETECTED")"
  "$(env_line JAVA_HOME_DIR "$JAVA_HOME_DETECTED")"
  "$(env_line MAVEN_HOME "$MAVEN_HOME_DETECTED")"
  "$(env_line NODE_HOME "$NODE_HOME_DETECTED")"
  "$(env_line DB_HOST localhost)"
  "$(env_line DB_PORT 5432)"
  "$(env_line DB_NAME scenariogenerator)"
  "$(env_line DB_USER postgres)"
)

echo "Détection de l'environnement (lecture seule)"
printf '  %-17s %s\n' ROOT "$ROOT_DETECTED"
printf '  %-17s %s\n' POSTGRES_HOME "${POSTGRES_HOME_DETECTED:-NON TROUVÉ}"
printf '  %-17s %s\n' JAVA_HOME_DIR "${JAVA_HOME_DETECTED:-NON TROUVÉ}"
printf '  %-17s %s\n' JAVA_VERSION "$JAVA_VERSION"
printf '  %-17s %s\n' MAVEN_HOME "${MAVEN_HOME_DETECTED:-NON TROUVÉ}"
printf '  %-17s %s\n' NODE_HOME "${NODE_HOME_DETECTED:-NON TROUVÉ}"
echo
echo "Valeurs proposées :"
printf '%s\n' "${ENV_LINES[@]}"
echo
echo "DB_PASSWORD est volontairement absent."

if [[ -n "$WRITE_FILE" ]]; then
  for required in "$POSTGRES_HOME_DETECTED" "$JAVA_HOME_DETECTED" "$MAVEN_HOME_DETECTED"; do
    [[ -n "$required" ]] || {
      echo "[FAIL] Outil obligatoire non détecté : aucun fichier écrit." >&2
      exit 1
    }
  done
  [[ ! -e "$WRITE_FILE" || "${OVERWRITE_PLATFORM_ENV:-false}" == "true" ]] || {
    echo "[FAIL] $WRITE_FILE existe déjà. Définir OVERWRITE_PLATFORM_ENV=true pour le remplacer." >&2
    exit 1
  }
  umask 077
  {
    echo "# Configuration locale générée le $(date '+%Y-%m-%d %H:%M:%S')"
    echo "# Ne pas ajouter DB_PASSWORD et ne pas committer ce fichier."
    printf '%s\n' "${ENV_LINES[@]}"
  } >"$WRITE_FILE"
  echo "[OK] Configuration locale écrite : $WRITE_FILE"

  PATH_SCRIPT="$(cd "$(dirname "$WRITE_FILE")" && pwd)/platform-path.sh"
  PLATFORM_ENV_ABSOLUTE="$(cd "$(dirname "$WRITE_FILE")" && pwd)/$(basename "$WRITE_FILE")"
  printf -v PLATFORM_ENV_ESCAPED '%q' "$PLATFORM_ENV_ABSOLUTE"
  printf -v LOADER_ESCAPED '%q' "$ROOT_DETECTED/deploiement/common/runtime/platform-env.sh"
  {
    echo "#!/usr/bin/env bash"
    echo "# Généré localement par detect-env.sh. À charger avec :"
    echo "#   source \"$PATH_SCRIPT\""
    echo "export PLATFORM_ENV_FILE=$PLATFORM_ENV_ESCAPED"
    echo "source $LOADER_ESCAPED"
    echo 'export PATH="$NODE_HOME:$JAVA_HOME_DIR/bin:$MAVEN_HOME/bin:$POSTGRES_HOME/bin:$PATH"'
    echo 'echo "[OK] Environnement et PATH chargés pour $ROOT"'
  } >"$PATH_SCRIPT"
  chmod +x "$PATH_SCRIPT"
  echo "[OK] Script PATH local écrit : $PATH_SCRIPT"
  echo "Charger maintenant avec : source \"$PATH_SCRIPT\""
fi
