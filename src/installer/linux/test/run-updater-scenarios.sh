#!/bin/sh
# Cenarios do `linux-updater.sh`, em POSIX sh.
#
# Molde do `Invoke-UpdateScenarios.ps1` do Windows: exercita o script REAL, o
# mesmo recurso que vai empacotado, e nao uma copia adaptada. Cenario que testa
# uma copia nao testa o que roda na maquina de quem instalou.
#
# Roda em qualquer sh POSIX -- `dash` no Ubuntu, `bash` no Arch e no Fedora --
# porque e essa a promessa do script: `/bin/sh` sem extensao de shell nenhuma.
#
# Uso: sh run-updater-scenarios.sh [caminho-do-linux-updater.sh]

set -u

UPDATER=${1:-src/desktopMain/resources/update/linux-updater.sh}

if [ ! -f "$UPDATER" ]; then
    printf 'nao encontrei o updater em %s\n' "$UPDATER" >&2
    exit 2
fi

UPDATER=$(cd "$(dirname "$UPDATER")" && pwd)/$(basename "$UPDATER")

# Tetos curtos: os dois timeouts precisam ser exercitados, e 60 s por cenario
# seria um roteiro que ninguem roda.
USAGE_MONITOR_UPDATER_PID_TIMEOUT=3
USAGE_MONITOR_UPDATER_ACK_TIMEOUT=3
USAGE_MONITOR_UPDATER_POLL_INTERVAL=1
export USAGE_MONITOR_UPDATER_PID_TIMEOUT USAGE_MONITOR_UPDATER_ACK_TIMEOUT USAGE_MONITOR_UPDATER_POLL_INTERVAL

checks=0
failures=0
scenario=''

start() {
    scenario=$1
    printf '\n=== %s ===\n' "$scenario"
}

check() {
    checks=$((checks + 1))
    if [ "$2" = "$3" ]; then
        printf '  ok   %s\n' "$1"
    else
        failures=$((failures + 1))
        printf '  FAIL %s\n       esperado: [%s]\n       obtido:   [%s]\n' "$1" "$2" "$3"
    fi
}

check_file_absent() {
    checks=$((checks + 1))
    if [ ! -e "$2" ]; then
        printf '  ok   %s\n' "$1"
    else
        failures=$((failures + 1))
        printf '  FAIL %s (existe: %s)\n' "$1" "$2"
    fi
}

check_file_present() {
    checks=$((checks + 1))
    if [ -e "$2" ]; then
        printf '  ok   %s\n' "$1"
    else
        failures=$((failures + 1))
        printf '  FAIL %s (ausente: %s)\n' "$1" "$2"
    fi
}

# Monta uma instalacao gerenciada. `$1` e a raiz, `$2` a versao ativa, `$3` a
# versao em staging (vazio para nenhuma).
build_tree() {
    tree_root=$1
    tree_current=$2
    tree_staging=$3

    mkdir -p "$tree_root/versions/$tree_current/Usage Monitor/bin"
    printf '#!/bin/sh\nexit 0\n' > "$tree_root/versions/$tree_current/Usage Monitor/bin/Usage Monitor"
    chmod 0755 "$tree_root/versions/$tree_current/Usage Monitor/bin/Usage Monitor"
    : > "$tree_root/.usage-monitor-managed"
    printf '%s\n' "$tree_current" > "$tree_root/current"

    if [ -n "$tree_staging" ]; then
        mkdir -p "$tree_root/updates/$tree_staging.staging/Usage Monitor/bin"
        printf '#!/bin/sh\nexit 0\n' > "$tree_root/updates/$tree_staging.staging/Usage Monitor/bin/Usage Monitor"
        chmod 0755 "$tree_root/updates/$tree_staging.staging/Usage Monitor/bin/Usage Monitor"
    fi
}

# Launcher falso que grava o token do ACK. `$1` e o caminho, `$2` o arquivo de
# ACK, `$3` um atraso antes de gravar (vazio para nenhum), `$4` "silent" para um
# launcher que sobe e NUNCA confirma.
build_launcher() {
    launcher_path=$1
    launcher_ack=$2
    launcher_delay=$3
    launcher_mode=$4

    mkdir -p "$(dirname "$launcher_path")"
    {
        printf '#!/bin/sh\n'
        printf 'echo "launched $*" >> "%s.log"\n' "$launcher_path"
        if [ "$launcher_mode" != "silent" ]; then
            if [ -n "$launcher_delay" ]; then
                printf 'sleep %s\n' "$launcher_delay"
            fi
            printf 'for a in "$@"; do\n'
            printf '  case $a in --update-ack=*) printf "%%s" "${a#--update-ack=}" > "%s" ;; esac\n' "$launcher_ack"
            printf 'done\n'
        fi
    } > "$launcher_path"
    chmod 0755 "$launcher_path"
}

# O updater e SEMPRE copiado para o diretorio do cenario antes de rodar.
#
# Nao e higiene: o script se apaga (`rm -f "$0"`) no caminho de sucesso, e
# invoca-lo direto do repositorio apagaria o recurso versionado. Aconteceu na
# primeira execucao deste roteiro. E tambem e o que o app faz -- ele materializa
# uma copia, nunca executa o recurso no lugar.
updater_copy() {
    cp "$UPDATER" "$1/updater.sh"
    chmod 0755 "$1/updater.sh"
    printf '%s' "$1/updater.sh"
}

read_current() {
    cat "$1/current" 2>/dev/null
}

receipt_field() {
    grep "^$2=" "$1" 2>/dev/null | cut -d= -f2-
}

# --- S1: swap com ACK bem-sucedido ----------------------------------------

start 'S1 swap com ACK bem-sucedido'
work=$(mktemp -d)
build_tree "$work/root" 38.0.0 39.0.0
build_launcher "$work/bin/usage-monitor" "$work/ack" '' normal
sh "$(updater_copy "$work")" "$work/root" 39.0.0 38.0.0 999999 tok-s1 \
    "$work/bin/usage-monitor" "$work/ack" "$work/receipt" > "$work/log" 2>&1
check 'exit 0' 0 $?
check 'current aponta para a versao nova' 39.0.0 "$(read_current "$work/root")"
check 'recibo de sucesso' success "$(receipt_field "$work/receipt" status)"
check 'recibo nomeia a versao anterior' 38.0.0 "$(receipt_field "$work/receipt" previousVersion)"
check_file_present 'a arvore nova foi promovida' "$work/root/versions/39.0.0/Usage Monitor/bin/Usage Monitor"
check_file_absent 'o staging foi limpo' "$work/root/updates/39.0.0.staging"
check_file_absent 'o script se apagou' "$work/updater.sh"
rm -rf "$work"

# --- S2: processo anterior demorando para sair -----------------------------

start 'S2 processo anterior demorando para sair'
work=$(mktemp -d)
build_tree "$work/root" 38.0.0 39.0.0
build_launcher "$work/bin/usage-monitor" "$work/ack" '' normal
sh -c 'sleep 2' &
slow_pid=$!
sh "$(updater_copy "$work")" "$work/root" 39.0.0 38.0.0 "$slow_pid" tok-s2 \
    "$work/bin/usage-monitor" "$work/ack" "$work/receipt" > "$work/log" 2>&1
check 'exit 0 depois de esperar' 0 $?
check 'current aponta para a versao nova' 39.0.0 "$(read_current "$work/root")"
check 'recibo de sucesso' success "$(receipt_field "$work/receipt" status)"
rm -rf "$work"

# --- S3: timeout de PID sem alterar current --------------------------------

start 'S3 timeout de PID nao altera current'
work=$(mktemp -d)
build_tree "$work/root" 38.0.0 39.0.0
build_launcher "$work/bin/usage-monitor" "$work/ack" '' normal
sh -c 'sleep 30' &
stuck_pid=$!
sh "$(updater_copy "$work")" "$work/root" 39.0.0 38.0.0 "$stuck_pid" tok-s3 \
    "$work/bin/usage-monitor" "$work/ack" "$work/receipt" > "$work/log" 2>&1
check 'exit 1' 1 $?
kill "$stuck_pid" 2>/dev/null
check 'current INTACTO' 38.0.0 "$(read_current "$work/root")"
check 'recibo de falha' failed "$(receipt_field "$work/receipt" status)"
check 'motivo nomeado' previous-process-alive "$(receipt_field "$work/receipt" reason)"
check_file_absent 'a versao nova NAO foi promovida' "$work/root/versions/39.0.0"
rm -rf "$work"

# --- S4: falha de lancamento com rollback ----------------------------------

start 'S4 falha de lancamento faz rollback'
work=$(mktemp -d)
build_tree "$work/root" 38.0.0 39.0.0
# Staging sem o executavel: a arvore promovida nao teria o que lancar.
rm -f "$work/root/updates/39.0.0.staging/Usage Monitor/bin/Usage Monitor"
build_launcher "$work/bin/usage-monitor" "$work/ack" '' normal
sh "$(updater_copy "$work")" "$work/root" 39.0.0 38.0.0 999999 tok-s4 \
    "$work/bin/usage-monitor" "$work/ack" "$work/receipt" > "$work/log" 2>&1
check 'exit 1' 1 $?
check 'current restaurado' 38.0.0 "$(read_current "$work/root")"
check 'motivo nomeado' launch-failed "$(receipt_field "$work/receipt" reason)"
sleep 1
check_file_present 'a versao anterior foi relancada' "$work/bin/usage-monitor.log"
rm -rf "$work"

# --- S5: health timeout com rollback e relancamento -------------------------

start 'S5 health timeout faz rollback e relanca a anterior'
work=$(mktemp -d)
build_tree "$work/root" 38.0.0 39.0.0
build_launcher "$work/bin/usage-monitor" "$work/ack" '' silent
sh "$(updater_copy "$work")" "$work/root" 39.0.0 38.0.0 999999 tok-s5 \
    "$work/bin/usage-monitor" "$work/ack" "$work/receipt" > "$work/log" 2>&1
check 'exit 1' 1 $?
check 'current restaurado' 38.0.0 "$(read_current "$work/root")"
check 'motivo nomeado' health-timeout "$(receipt_field "$work/receipt" reason)"
sleep 1
check 'duas tentativas de lancamento' 2 "$(wc -l < "$work/bin/usage-monitor.log" | tr -d ' ')"
rm -rf "$work"

# --- S6: diretorios com espacos --------------------------------------------

start 'S6 diretorios com espacos'
work=$(mktemp -d)/'caminho com espaco'
mkdir -p "$work"
build_tree "$work/root dir" 38.0.0 39.0.0
build_launcher "$work/bin dir/usage-monitor" "$work/ack file" '' normal
sh "$(updater_copy "$work")" "$work/root dir" 39.0.0 38.0.0 999999 tok-s6 \
    "$work/bin dir/usage-monitor" "$work/ack file" "$work/receipt file" > "$work/log" 2>&1
check 'exit 0' 0 $?
check 'current aponta para a versao nova' 39.0.0 "$(read_current "$work/root dir")"
check 'recibo de sucesso' success "$(receipt_field "$work/receipt file" status)"
rm -rf "$(dirname "$work")"

# --- S7: staging ausente ----------------------------------------------------

start 'S7 staging ausente aborta sem tocar em nada'
work=$(mktemp -d)
build_tree "$work/root" 38.0.0 ''
build_launcher "$work/bin/usage-monitor" "$work/ack" '' normal
sh "$(updater_copy "$work")" "$work/root" 39.0.0 38.0.0 999999 tok-s7 \
    "$work/bin/usage-monitor" "$work/ack" "$work/receipt" > "$work/log" 2>&1
check 'exit 1' 1 $?
check 'current INTACTO' 38.0.0 "$(read_current "$work/root")"
check 'motivo nomeado' missing-staging "$(receipt_field "$work/receipt" reason)"
rm -rf "$work"

# --- S8: versao fora do formato --------------------------------------------

start 'S8 versao que escaparia da raiz e recusada'
work=$(mktemp -d)
build_tree "$work/root" 38.0.0 39.0.0
build_launcher "$work/bin/usage-monitor" "$work/ack" '' normal
sh "$(updater_copy "$work")" "$work/root" '../../etc' 38.0.0 999999 tok-s8 \
    "$work/bin/usage-monitor" "$work/ack" "$work/receipt" > "$work/log" 2>&1
check 'exit 1' 1 $?
check 'current INTACTO' 38.0.0 "$(read_current "$work/root")"
check 'motivo nomeado' invalid-version "$(receipt_field "$work/receipt" reason)"
rm -rf "$work"

# --- S9: marcador ausente ---------------------------------------------------

start 'S9 marcador ausente aborta sem tocar em nada'
work=$(mktemp -d)
build_tree "$work/root" 38.0.0 39.0.0
rm -f "$work/root/.usage-monitor-managed"
build_launcher "$work/bin/usage-monitor" "$work/ack" '' normal
sh "$(updater_copy "$work")" "$work/root" 39.0.0 38.0.0 999999 tok-s9 \
    "$work/bin/usage-monitor" "$work/ack" "$work/receipt" > "$work/log" 2>&1
check 'exit 1' 1 $?
check 'current INTACTO' 38.0.0 "$(read_current "$work/root")"
# Sem marcador a arvore nao e nossa, e ate o recibo seria uma escrita a mais.
check_file_absent 'nenhum recibo foi escrito' "$work/receipt"
check_file_present 'o staging NAO foi limpo' "$work/root/updates/39.0.0.staging"
rm -rf "$work"

# --- S10: retencao de uma versao anterior e poda ----------------------------

start 'S10 poda mantem a atual e uma anterior'
work=$(mktemp -d)
build_tree "$work/root" 38.0.0 39.0.0
mkdir -p "$work/root/versions/30.0.0" "$work/root/versions/37.0.0"
build_launcher "$work/bin/usage-monitor" "$work/ack" '' normal
sh "$(updater_copy "$work")" "$work/root" 39.0.0 38.0.0 999999 tok-s10 \
    "$work/bin/usage-monitor" "$work/ack" "$work/receipt" > "$work/log" 2>&1
check 'exit 0' 0 $?
check_file_present 'a versao nova ficou' "$work/root/versions/39.0.0"
check_file_present 'a anterior ficou, para rollback' "$work/root/versions/38.0.0"
check_file_absent 'a de tres ciclos atras saiu' "$work/root/versions/37.0.0"
check_file_absent 'a mais antiga saiu' "$work/root/versions/30.0.0"
rm -rf "$work"

# --- S11: promover por cima da versao em execucao --------------------------

start 'S11 promover a mesma versao e recusado'
work=$(mktemp -d)
build_tree "$work/root" 39.0.0 39.0.0
build_launcher "$work/bin/usage-monitor" "$work/ack" '' normal
sh "$(updater_copy "$work")" "$work/root" 39.0.0 39.0.0 999999 tok-s11 \
    "$work/bin/usage-monitor" "$work/ack" "$work/receipt" > "$work/log" 2>&1
check 'exit 1' 1 $?
check 'current INTACTO' 39.0.0 "$(read_current "$work/root")"
check 'motivo nomeado' same-version "$(receipt_field "$work/receipt" reason)"
check_file_present 'a arvore em execucao NAO foi apagada' "$work/root/versions/39.0.0/Usage Monitor/bin/Usage Monitor"
rm -rf "$work"

# --- resumo -----------------------------------------------------------------

printf '\n===========================\n'
printf '%s verificacoes, %s falhas\n' "$checks" "$failures"

if [ "$failures" -ne 0 ]; then
    exit 1
fi
