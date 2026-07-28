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
  local drive name candidate depth
  local -a depths=(4 6 "$DETECT_MAX_DEPTH")
  for depth in "${depths[@]}"; do
    (( depth <= DETECT_MAX_DEPTH )) || continue
    for drive in "${DETECT_DRIVE_ROOTS[@]}"; do
      for name in "$@"; do
        candidate="$(find "$drive" -maxdepth "$depth" -type f \
          -iname "$name" -print -quit 2>/dev/null || true)"
        [[ -n "$candidate" ]] && {
          normalize_path "$candidate"
          return 0
        }
      done
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

home_from_executable() {
  local executable="$1" parent
  [[ -n "$executable" ]] || { printf '\n'; return 0; }
  parent="$(dirname "$executable")"
  [[ "$(basename "$parent")" != "bin" ]] || parent="$(dirname "$parent")"
  printf '%s\n' "$parent"
}

valid_jdk_home() {
  local home="$1" lowered java_bin version_line major
  [[ -n "$home" ]] || return 1
  home="$(normalize_path "$home")"
  lowered="${home,,}"
  if [[ "$lowered" =~ /jbr(/|$)|idea-|intellij|/plugins/ ]]; then
    return 1
  fi
  [[ -f "$home/bin/java.exe" || -f "$home/bin/java" ]] || return 1
  [[ -f "$home/bin/javac.exe" || -f "$home/bin/javac" ]] || return 1
  java_bin="$home/bin/java.exe"
  [[ -f "$java_bin" ]] || java_bin="$home/bin/java"
  version_line="$("$java_bin" -version 2>&1 | head -1 || true)"
  major="$(sed -nE 's/.*version "([0-9]+).*/\1/p' <<<"$version_line")"
  [[ "$major" =~ ^[0-9]+$ && "$major" -ge 21 ]]
}

discover_jdk() {
  local configured="${JAVA_HOME_DIR:-${JAVA_HOME:-}}" candidate home drive depth
  if valid_jdk_home "$configured"; then
    normalize_path "$configured"
    return 0
  fi

  candidate="$(find_on_path java.exe java)"
  home="$(home_from_executable "$candidate")"
  if valid_jdk_home "$home"; then
    printf '%s\n' "$home"
    return 0
  fi

  if command -v where.exe >/dev/null 2>&1; then
    while IFS= read -r candidate; do
      candidate="$(normalize_path "$(tr -d '\r' <<<"$candidate")")"
      home="$(home_from_executable "$candidate")"
      if valid_jdk_home "$home"; then
        printf '%s\n' "$home"
        return 0
      fi
    done < <(where.exe java.exe 2>/dev/null || true)
  fi

  echo "[INFO] Recherche d'un JDK complet (java + javac)..." >&2
  for depth in 4 6 "$DETECT_MAX_DEPTH"; do
    (( depth <= DETECT_MAX_DEPTH )) || continue
    for drive in "${DETECT_DRIVE_ROOTS[@]}"; do
      while IFS= read -r candidate; do
        home="$(dirname "$(dirname "$(normalize_path "$candidate")")")"
        if valid_jdk_home "$home"; then
          printf '%s\n' "$home"
          return 0
        fi
      done < <(find "$drive" -maxdepth "$depth" -type f \
        \( -iname javac.exe -o -iname javac \) -print 2>/dev/null || true)
    done
  done
  return 0
}

valid_node_home() {
  local home="$1"
  [[ -n "$home" ]] || return 1
  [[ -f "$home/node.exe" || -f "$home/bin/node.exe" ||
     -f "$home/bin/node" ]] || return 1
  [[ -f "$home/npm.cmd" || -f "$home/bin/npm.cmd" ||
     -f "$home/bin/npm" ]] || return 1
}

discover_node_home() {
  local configured="${NODE_HOME:-}" candidate home
  if valid_node_home "$configured"; then
    normalize_path "$configured"
    return 0
  fi
  candidate="$(find_on_path node.exe node)"
  [[ -n "$candidate" ]] || candidate="$(find_with_where node.exe node)"
  home="$(home_from_executable "$candidate")"
  if valid_node_home "$home"; then
    printf '%s\n' "$home"
    return 0
  fi
  echo "[INFO] Recherche de la racine Node.js (node + npm)..." >&2
  candidate="$(find_on_drives node.exe node)"
  home="$(home_from_executable "$candidate")"
  valid_node_home "$home" && printf '%s\n' "$home"
  return 0
}

PSQL_FOUND="$(discover_tool PostgreSQL "${POSTGRES_HOME:-}" \
  'bin/psql.exe bin/psql' psql.exe psql)"
JAVA_HOME_DETECTED="$(discover_jdk)"
JAVA_FOUND=""
[[ -z "$JAVA_HOME_DETECTED" ]] ||
  JAVA_FOUND="$(find_from_home "$JAVA_HOME_DETECTED" bin/java.exe bin/java)"
MAVEN_FOUND="$(discover_tool Maven "${MAVEN_HOME:-}" \
  'bin/mvn.cmd bin/mvn' mvn.cmd mvn)"
NODE_HOME_DETECTED="$(discover_node_home)"

POSTGRES_HOME_DETECTED="$(home_from_executable "$PSQL_FOUND")"
MAVEN_HOME_DETECTED="$(home_from_executable "$MAVEN_FOUND")"
PGDATA_DETECTED="${PGDATA:-}"
if [[ -z "$PGDATA_DETECTED" && -n "$POSTGRES_HOME_DETECTED" ]]; then
  PG_VERSION_FILE="$(find "$POSTGRES_HOME_DETECTED" -maxdepth 4 -type f \
    -name PG_VERSION -print -quit 2>/dev/null || true)"
  [[ -z "$PG_VERSION_FILE" ]] || PGDATA_DETECTED="$(dirname "$PG_VERSION_FILE")"
fi
JAVA_VERSION="non détectée"
[[ -z "$JAVA_FOUND" ]] || JAVA_VERSION="$("$JAVA_FOUND" -version 2>&1 | head -1)"
JAVA_MAJOR="$(sed -nE 's/.*version "([0-9]+).*/\1/p' <<<"$JAVA_VERSION")"
JAVA_SUPPORT="JDK introuvable"
if [[ "$JAVA_MAJOR" == "21" ]]; then
  JAVA_SUPPORT="JDK 21 de référence"
elif [[ "$JAVA_MAJOR" =~ ^[0-9]+$ && "$JAVA_MAJOR" -gt 21 ]]; then
  JAVA_SUPPORT="JDK récent supporté par la pile de tests actualisée"
fi

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
  "$(env_line PGDATA "$PGDATA_DETECTED")"
  "$(env_line POSTGRES_SERVICE_NAME "${POSTGRES_SERVICE_NAME:-}")"
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
printf '  %-17s %s\n' JAVA_SUPPORT "$JAVA_SUPPORT"
printf '  %-17s %s\n' MAVEN_HOME "${MAVEN_HOME_DETECTED:-NON TROUVÉ}"
printf '  %-17s %s\n' NODE_HOME "${NODE_HOME_DETECTED:-NON TROUVÉ}"
printf '  %-17s %s\n' PGDATA "${PGDATA_DETECTED:-NON TROUVÉ}"
printf '  %-17s %s\n' POSTGRES_SERVICE_NAME "${POSTGRES_SERVICE_NAME:-NON RENSEIGNÉ}"
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
