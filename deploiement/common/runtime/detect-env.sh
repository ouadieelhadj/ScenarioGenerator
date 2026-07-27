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

first_file() {
  local pattern match
  for pattern in "$@"; do
    while IFS= read -r match; do
      [[ -f "$match" ]] && { printf '%s\n' "$match"; return; }
    done < <(compgen -G "$pattern" 2>/dev/null || true)
  done
}

to_home() {
  local executable="$1" suffix="$2"
  [[ -n "$executable" ]] || return
  printf '%s\n' "${executable%/$suffix}"
}

PSQL_FOUND="$(first_file \
  /c/MoneyCore/PostgreSQL/*/bin/psql.exe /d/MoneyCore/PostgreSQL/*/bin/psql.exe /f/MoneyCore/PostgreSQL/*/bin/psql.exe \
  '/c/Program Files/PostgreSQL/*/bin/psql.exe' '/d/Program Files/PostgreSQL/*/bin/psql.exe' '/f/Program Files/PostgreSQL/*/bin/psql.exe' \
  /c/PostgreSQL/*/bin/psql.exe /d/PostgreSQL/*/bin/psql.exe /f/PostgreSQL/*/bin/psql.exe)"
JAVA_FOUND="$(first_file \
  /c/MoneyCore/jdk*/bin/java.exe /d/MoneyCore/jdk*/bin/java.exe /f/MoneyCore/jdk*/bin/java.exe \
  '/c/Program Files/Java/jdk*/bin/java.exe' '/d/Program Files/Java/jdk*/bin/java.exe' '/f/Program Files/Java/jdk*/bin/java.exe' \
  /c/jdk*/bin/java.exe /d/jdk*/bin/java.exe /f/jdk*/bin/java.exe)"
MAVEN_FOUND="$(first_file \
  /c/MoneyCore/apache-maven-*/bin/mvn.cmd /d/MoneyCore/apache-maven-*/bin/mvn.cmd /f/MoneyCore/apache-maven-*/bin/mvn.cmd \
  /c/MoneyCore/idea-*/plugins/maven/lib/maven3/bin/mvn.cmd /d/MoneyCore/idea-*/plugins/maven/lib/maven3/bin/mvn.cmd /f/MoneyCore/idea-*/plugins/maven/lib/maven3/bin/mvn.cmd \
  '/c/Program Files/Apache/maven*/bin/mvn.cmd' '/d/Program Files/Apache/maven*/bin/mvn.cmd' '/f/Program Files/Apache/maven*/bin/mvn.cmd')"
NODE_FOUND="$(first_file \
  /c/MoneyCore/nodejs/npm.cmd /d/MoneyCore/nodejs/npm.cmd /f/MoneyCore/nodejs/npm.cmd \
  '/c/Program Files/nodejs/npm.cmd' '/d/Program Files/nodejs/npm.cmd' '/f/Program Files/nodejs/npm.cmd')"

POSTGRES_HOME_DETECTED="$(to_home "$PSQL_FOUND" bin/psql.exe)"
JAVA_HOME_DETECTED="$(to_home "$JAVA_FOUND" bin/java.exe)"
MAVEN_HOME_DETECTED="$(to_home "$MAVEN_FOUND" bin/mvn.cmd)"
NODE_HOME_DETECTED="$(to_home "$NODE_FOUND" npm.cmd)"
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
fi
