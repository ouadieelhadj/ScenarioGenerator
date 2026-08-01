#!/usr/bin/env bash

DMAS_DMC_DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../../common/runtime/platform-env.sh
source "$DMAS_DMC_DEPLOY_DIR/../../common/runtime/platform-env.sh"

export DMAS_DMC_RUNTIME="${DMAS_DMC_RUNTIME:-$ROOT/runtime/dmas-dmc}"
export DMAS_DMC_LOG_DIR="${DMAS_DMC_LOG_DIR:-$DMAS_DMC_RUNTIME/logs}"
export DMAS_DMC_PID_DIR="${DMAS_DMC_PID_DIR:-$DMAS_DMC_RUNTIME/pids}"

export DMAS_MEMBER_URL="${DMAS_MEMBER_URL:-http://localhost:8084}"
export DMAS_MASTERCARD_URL="${DMAS_MASTERCARD_URL:-http://localhost:8501}"
export DMCS_ACQUIRER_URL="${DMCS_ACQUIRER_URL:-http://localhost:8082}"
export DMCS_ISSUER_URL="${DMCS_ISSUER_URL:-http://localhost:8083}"
export DMAS_MASTERCARD_ISO_PORT="${DMAS_MASTERCARD_ISO_PORT:-8500}"

export DMAS_MEMBER_INTERFACE="${DMAS_MEMBER_INTERFACE:-DMAS_BANK_A}"
export DMAS_MASTERCARD_INTERFACE="${DMAS_MASTERCARD_INTERFACE:-DMAS_MASTERCARD_1}"
export DMAS_MEMBER_GROUP_ID="${DMAS_MEMBER_GROUP_ID:-TESTGRP01}"
export DMAS_MEMBER_BANK_CODE="${DMAS_MEMBER_BANK_CODE:-022905}"
export DMAS_ADMIN_LOGIN="${DMAS_ADMIN_LOGIN:-admin}"
export DMCS_BASIC_USER="${DMCS_BASIC_USER:-admin}"
export DMCS_BASIC_PASSWORD="${DMCS_BASIC_PASSWORD:-Admin123!}"
export DMAS_MEMBER_LMK_FILE="${DMAS_MEMBER_LMK_FILE:-$ROOT/keys/dmas-lmk-acq.lmk}"
export DMAS_MASTERCARD_LMK_FILE="${DMAS_MASTERCARD_LMK_FILE:-$ROOT/keys/dmas-lmk-iss.lmk}"
export DMCS_ACQUIRER_BASE_DIR="${DMCS_ACQUIRER_BASE_DIR:-$DMAS_DMC_RUNTIME/dmcs/acquirer}"
export DMCS_ISSUER_BASE_DIR="${DMCS_ISSUER_BASE_DIR:-$DMAS_DMC_RUNTIME/dmcs/issuer}"

mkdir -p "$DMAS_DMC_LOG_DIR" "$DMAS_DMC_PID_DIR" \
  "$DMCS_ACQUIRER_BASE_DIR" "$DMCS_ISSUER_BASE_DIR"

dmas_url_port() {
  printf '%s' "$1" | sed -nE 's#^[a-zA-Z]+://[^/:]+:([0-9]+).*$#\1#p'
}

dmas_pid_alive() {
  kill -0 "$1" 2>/dev/null || {
    local win_pid
    win_pid="$(dmas_windows_pid "$1")"
    [[ -n "$win_pid" ]] && tasklist.exe //FI "PID eq $win_pid" //FO CSV //NH 2>/dev/null |
      tr -d '\r' | grep -q "\"$win_pid\""
  }
}

dmas_windows_pid() {
  local value="$1"
  ps -W 2>/dev/null | awk -v target="$value" \
    'NR > 1 && ($1 == target || $4 == target) {print $4; exit}'
}

dmas_posix_pid() {
  local value="$1"
  ps -W 2>/dev/null | awk -v target="$value" \
    'NR > 1 && ($1 == target || $4 == target) {print $1; exit}'
}

dmas_listening_pids() {
  local port="$1"
  netstat -ano 2>/dev/null |
    awk -v suffix=":$port" \
      '$2 ~ suffix"$" && $4=="LISTENING" {print $5}' |
    tr -d '\r' | sort -u
}

dmas_pid_matches_module() {
  local pid="$1" module="$2"
  powershell.exe -NoProfile -Command \
    "\$p=Get-CimInstance Win32_Process -Filter \"ProcessId=$pid\" -ErrorAction SilentlyContinue; if (\$p -and \$p.CommandLine -like '*$module*') { exit 0 }; exit 1" \
    >/dev/null 2>&1
}

dmas_wait_port() {
  local port="$1" label="$2"
  for _ in $(seq 1 90); do
    [[ -n "$(dmas_listening_pids "$port")" ]] && {
      echo "[OK] $label UP (port $port)"
      return 0
    }
    sleep 1
  done
  echo "[FAIL] $label indisponible sur le port $port" >&2
  return 1
}

dmas_wait_http() {
  local url="$1" label="$2"
  for _ in $(seq 1 90); do
    curl -fsS "$url" >/dev/null 2>&1 && {
      echo "[OK] $label UP"
      return 0
    }
    sleep 1
  done
  echo "[FAIL] $label indisponible : $url" >&2
  return 1
}

dmas_require_secret() {
  local name="$1" label="${2:-$1}" value="${!1:-}"
  if [[ -z "$value" ]]; then
    read -r -s -p "$label: " value
    echo
    printf -v "$name" '%s' "$value"
    export "$name"
  fi
  [[ -n "${!name:-}" ]] || {
    echo "[FAIL] $name obligatoire" >&2
    return 1
  }
}

dmas_login() {
  local base_url="$1"
  dmas_require_secret DMAS_ADMIN_PASSWORD "Mot de passe administrateur DMAS"
  curl -fsS -X POST "$base_url/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"login\":\"$DMAS_ADMIN_LOGIN\",\"password\":\"$DMAS_ADMIN_PASSWORD\"}" |
    sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
}

dmas_auth_post() {
  local base_url="$1" path="$2" token="$3"
  shift 3
  curl -fsS -X POST "$base_url$path" \
    -H "Authorization: Bearer $token" "$@"
}

dmas_build_module() {
  "$MAVEN" -f "$ROOT/pom.xml" -pl "$1" -am package -DskipTests \
    -Dmaven.repo.local="$MAVEN_REPO"
}

dmas_find_jar() {
  find "$ROOT/$1/target" -maxdepth 1 -type f -name "$1-*.jar" \
    ! -name '*.original' | head -1
}

dmas_start_module() {
  local module="$1" port="$2"
  shift 2
  local pid_file="$DMAS_DMC_PID_DIR/$module.pid"
  local console_log="$DMAS_DMC_LOG_DIR/$module-console.log"
  local jar pid old_dir

  if [[ -f "$pid_file" ]]; then
    pid="$(tr -d '[:space:]' <"$pid_file")"
    if dmas_pid_alive "$pid" && dmas_pid_matches_module "$pid" "$module"; then
      dmas_wait_port "$port" "$module deja demarre"
      return
    fi
    rm -f "$pid_file"
  fi

  if [[ -n "$(dmas_listening_pids "$port")" ]]; then
    echo "[FAIL] Port $port deja occupe; aucun processus n'a ete arrete." >&2
    return 1
  fi

  jar="$(dmas_find_jar "$module")"
  if [[ -z "$jar" ]]; then
    dmas_build_module "$module"
    jar="$(dmas_find_jar "$module")"
  fi
  [[ -n "$jar" ]] || {
    echo "[FAIL] JAR absent pour $module" >&2
    return 1
  }

  old_dir="$PWD"
  cd "$ROOT"
  nohup "$JAVA" -jar "$jar" "$@" >"$console_log" 2>&1 &
  pid=$!
  cd "$old_dir"
  echo "$pid" >"$pid_file"

  if ! dmas_wait_port "$port" "$module"; then
    tail -60 "$console_log" >&2 || true
    return 1
  fi
}

dmas_stop_module() {
  local module="$1" port="$2" pid="" candidate win_pid posix_pid
  local pid_file="$DMAS_DMC_PID_DIR/$module.pid"

  if [[ -f "$pid_file" ]]; then
    pid="$(tr -d '[:space:]' <"$pid_file")"
    win_pid="$(dmas_windows_pid "$pid")"
    # The PID file is written by dmas_start_module in this runtime. It is the
    # trusted ownership proof; WMI command-line inspection may be denied on
    # hardened Windows workstations.
    if dmas_pid_alive "$pid" && [[ -n "$win_pid" ]]; then
      kill "$pid" 2>/dev/null || true
      sleep 1
      kill -9 "$pid" 2>/dev/null || true
      taskkill.exe //T //F //PID "$win_pid" >/dev/null 2>&1 || true
      echo "[STOP] $module PID=$pid"
    fi
    rm -f "$pid_file"
  fi

  for candidate in $(dmas_listening_pids "$port"); do
    if dmas_pid_matches_module "$candidate" "$module"; then
      posix_pid="$(dmas_posix_pid "$candidate")"
      [[ -n "$posix_pid" ]] && kill "$posix_pid" 2>/dev/null || true
      sleep 1
      [[ -n "$posix_pid" ]] && kill -9 "$posix_pid" 2>/dev/null || true
      taskkill.exe //T //F //PID "$candidate" >/dev/null 2>&1 || true
      echo "[STOP] $module detecte sur le port $port, PID=$candidate"
    else
      echo "[WARN] Port $port utilise par un autre processus PID=$candidate; non arrete." >&2
    fi
  done

  for _ in $(seq 1 20); do
    [[ -z "$(dmas_listening_pids "$port")" ]] && return 0
    sleep 1
  done
  echo "[FAIL] Port $port encore occupe apres l'arret cible de $module" >&2
  return 1
}

dmas_psql() {
  dmas_require_secret DB_PASSWORD "Mot de passe PostgreSQL"
  PGPASSWORD="$DB_PASSWORD" "$PSQL" --no-password \
    --host="$DB_HOST" --port="$DB_PORT" --username="$DB_USER" \
    --dbname="$DB_NAME" "$@"
}

dmas_to_windows_path() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -am "$1"
  else
    printf '%s' "$1"
  fi
}
