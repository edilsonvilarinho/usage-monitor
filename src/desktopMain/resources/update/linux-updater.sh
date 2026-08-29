#!/bin/sh
# Troca a versao ativa do Usage Monitor numa instalacao XDG gerenciada.
#
# Recurso versionado do app. O Kotlin o materializa em modo 0700 e o executa por
# /bin/sh com os argumentos SEPARADOS no ProcessBuilder: nenhum caminho e
# interpolado dentro deste arquivo, entao um apostrofo ou um espaco num nome de
# pasta nao tem como virar comando.
#
#   $1 root             raiz gerenciada (<XDG_DATA_HOME>/usage-monitor)
#   $2 version          versao a promover
#   $3 previous_version versao que esta saindo, ou "-" quando desconhecida
#   $4 previous_pid     PID do processo que esta encerrando
#   $5 ack_token        token do health check
#   $6 launcher         launcher estavel (~/.local/bin/usage-monitor)
#   $7 ack_file         arquivo de ACK
#   $8 receipt_file     recibo lido pela tela de Configuracoes
#   $9 log_file         mesmo linux-update.log que a saida deste script usa
#
# Entrada vem de /dev/null e saida/erro vao para o log, os dois pelo
# ProcessBuilder. O processo relancado (passo 6) e o de rollback (dentro de
# `rollback`) escrevem no MESMO log_file -- antes iam para /dev/null, e um
# crash ali nao deixava rastro nenhum: foi assim que o health-timeout medido
# numa Bazzite real (issue #118) ficou sem causa por varias tentativas. Este
# script NAO apaga o archive baixado: quem descarta os ~125 MB e
# `shouldDiscardUpdateArtifacts`, no arranque seguinte do app.
#
# `set -e` de proposito NAO esta ligado: cada passo destrutivo tem tratamento
# proprio, e abortar no meio de um swap e o unico jeito de deixar a instalacao
# num estado que ninguem sabe descrever.

set -u

if [ "$#" -ne 9 ]; then
    printf 'usage: linux-updater.sh root version previous_version pid token launcher ack receipt log\n' >&2
    exit 2
fi

root=$1
version=$2
previous_version=$3
previous_pid=$4
ack_token=$5
launcher=$6
ack_file=$7
receipt_file=$8
log_file=$9

marker=$root/.usage-monitor-managed
current_file=$root/current
versions_dir=$root/versions
staging_dir=$root/updates/$version.staging
target_dir=$versions_dir/$version

# Os tres tetos sao ajustaveis por ambiente, e os defaults ficam aqui.
#
# Nao e configuracao: o app nunca define estas variaveis. Existem porque o
# harness precisa exercitar os dois timeouts, e um teste que leva 60 s para
# provar que um timeout dispara e um teste que ninguem roda -- entao ninguem
# descobre quando o ramo de rollback quebra.
PID_TIMEOUT_SECONDS=${USAGE_MONITOR_UPDATER_PID_TIMEOUT:-60}
ACK_TIMEOUT_SECONDS=${USAGE_MONITOR_UPDATER_ACK_TIMEOUT:-60}
POLL_INTERVAL=${USAGE_MONITOR_UPDATER_POLL_INTERVAL:-1}

log() {
    printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"
}

# O motivo vai para o recibo, que a tela exibe. Ele nasce de mensagem de erro do
# sistema, entao passa por um alfabeto restrito: o recibo e um .properties, e uma
# quebra de linha ali inventaria uma chave.
sanitize() {
    printf '%s' "$1" | tr -c 'a-zA-Z0-9._-' '-' | cut -c1-64
}

write_receipt() {
    receipt_status=$1
    receipt_reason=$2
    receipt_tmp=$receipt_file.tmp

    mkdir -p "$(dirname "$receipt_file")" 2>/dev/null
    {
        printf 'version=%s\n' "$version"
        if [ "$previous_version" != "-" ]; then
            printf 'previousVersion=%s\n' "$previous_version"
        fi
        printf 'status=%s\n' "$receipt_status"
        if [ -n "$receipt_reason" ]; then
            printf 'reason=%s\n' "$(sanitize "$receipt_reason")"
        fi
    } > "$receipt_tmp" 2>/dev/null && mv "$receipt_tmp" "$receipt_file" 2>/dev/null
}

# Limpa o staging e a si mesmo. O `rm -f "$0"` e a ULTIMA instrucao do arquivo:
# o `sh` le o script em blocos, e um arquivo desta ordem de grandeza ja foi lido
# inteiro muito antes daqui. O materializador do lado Kotlin tambem apaga um
# script sobrado antes de escrever o proximo, para o caso de esta linha nao
# rodar.
cleanup() {
    rm -rf "$staging_dir" 2>/dev/null
}

abort() {
    log "ABORT $1"
    write_receipt failed "$1"
    cleanup
    exit 1
}

# --- 1. validacao. Sem marcador, nada e tocado. ---

case $version in
    *[!0-9.]* | '' | *..* ) abort invalid-version ;;
esac

if [ ! -f "$marker" ]; then
    # Sem o marcador esta arvore nao foi criada pelo instalador do app, e o
    # unico movimento seguro e nao fazer nenhum.
    log "ABORT missing-marker"
    exit 1
fi

if [ ! -d "$root" ] || [ ! -d "$staging_dir" ]; then
    abort missing-staging
fi

if [ ! -x "$launcher" ]; then
    abort missing-launcher
fi

if [ "$version" = "$previous_version" ]; then
    # Promover por cima da versao em execucao apagaria a arvore que acabou de
    # sair, sem ter para onde voltar.
    abort same-version
fi

mkdir -p "$versions_dir" 2>/dev/null

# --- 2. espera o processo anterior sair ---

waited=0
while [ "$waited" -lt "$PID_TIMEOUT_SECONDS" ]; do
    if ! kill -0 "$previous_pid" 2>/dev/null; then
        break
    fi
    sleep "$POLL_INTERVAL"
    waited=$((waited + POLL_INTERVAL))
done

if kill -0 "$previous_pid" 2>/dev/null; then
    # `current` intacto: a instalacao continua a que estava.
    abort previous-process-alive
fi

# --- 3. promove o staging ---

if [ -e "$target_dir" ]; then
    rm -rf "$target_dir" 2>/dev/null
fi

if ! mv "$staging_dir" "$target_dir" 2>/dev/null; then
    abort promote-failed
fi

# --- 4. guarda o valor anterior de `current` ---

if [ -f "$current_file" ]; then
    saved_current=$(cat "$current_file" 2>/dev/null)
else
    saved_current=
fi

restore_current() {
    if [ -n "$saved_current" ]; then
        printf '%s\n' "$saved_current" > "$current_file.next" 2>/dev/null &&
            mv "$current_file.next" "$current_file" 2>/dev/null
    fi
}

# --- 5. swap atomico. `current` e arquivo regular, entao isto e um rename(2). ---

if ! printf '%s\n' "$version" > "$current_file.next" 2>/dev/null; then
    abort current-write-failed
fi

if ! mv "$current_file.next" "$current_file" 2>/dev/null; then
    rm -f "$current_file.next" 2>/dev/null
    abort swap-failed
fi

# --- 6. lanca a versao nova com o argumento privado do health check ---

rollback() {
    rollback_reason=$1
    restore_current
    log "ABORT $rollback_reason"
    write_receipt failed "$rollback_reason"
    cleanup
    # Relanca a versao anterior: o usuario fechou o app esperando que ele
    # voltasse, e voltar na versao velha e melhor que nao voltar. A saida vai
    # para o mesmo log_file, anexada -- se o relancamento tambem falhar, o
    # motivo fica registrado em vez de silencioso. `unset LD_LIBRARY_PATH`
    # pelo mesmo motivo do lancamento do passo 6 -- ver o comentario la.
    ( unset LD_LIBRARY_PATH; "$launcher" >>"$log_file" 2>&1 & ) 2>/dev/null
    exit 1
}

# A falha de lancamento e detectada AQUI e nao pelo retorno do `&`: um job em
# background devolve 0 imediatamente, e um `if !` em volta dele nunca dispara.
# `kill -0` sobre o filho tambem nao serve -- sem `wait`, um processo que ja
# morreu vira zumbi e continua respondendo. O que da para afirmar sem bloquear e
# que a arvore promovida tem o executavel que o launcher vai chamar; o resto e o
# que o timeout de ACK cobre.
#
# As aspas nao sao estilo: o nome tem espaco, e `var=$x/Usage Monitor/...` sem
# elas e parseado como uma atribuicao seguida de um COMANDO. Medido -- o script
# morria com `Monitor/bin/Usage: No such file or directory`, deixava `current`
# ja apontando para a versao nova e nao rodava rollback nenhum.
promoted_launcher="$target_dir/Usage Monitor/bin/Usage Monitor"
if [ ! -x "$promoted_launcher" ]; then
    rollback launch-failed
fi

rm -f "$ack_file" 2>/dev/null

# Saida para o log_file, anexada: se este processo cair antes do ACK, o
# motivo fica aqui em vez de sumir em /dev/null.
#
# O token vai por VARIAVEL DE AMBIENTE, nao por argumento `--update-ack=X`
# (endurecimento; nao era a causa raiz, ver abaixo). Variavel de ambiente
# nao passa por parser de argv nenhum.
#
# `unset LD_LIBRARY_PATH` E A CAUSA RAIZ do health-timeout medido ao vivo
# numa Bazzite real (issue #118, issue #121 documenta a investigacao
# inteira). Este processo e filho da JVM que esta saindo, e herda o
# `LD_LIBRARY_PATH` QUE ELA setou -- apontando para o `lib/app` da versao
# ANTERIOR. O launcher nativo do jpackage usa essa variavel para se
# autolocalizar; com ela ja setada (apontando para a versao errada), ele
# pula a propria etapa de autoconfiguracao e a JVM sobe sem saber qual
# classe rodar -- imprime o "uso" do `java` na propria saida e sai, sem
# nunca chegar em `main()`. `unset` aqui faz o launcher da versao nova se
# autoconfigurar do zero, como faz numa instalacao/execucao normal, onde
# nao ha JVM pai nenhuma para herdar a variavel errada.
( unset LD_LIBRARY_PATH; USAGE_MONITOR_UPDATE_ACK="$ack_token" "$launcher" >>"$log_file" 2>&1 & ) 2>/dev/null

# --- 7. espera o ACK ---

waited=0
acknowledged=0
while [ "$waited" -lt "$ACK_TIMEOUT_SECONDS" ]; do
    if [ -f "$ack_file" ] && [ "$(cat "$ack_file" 2>/dev/null)" = "$ack_token" ]; then
        acknowledged=1
        break
    fi
    sleep "$POLL_INTERVAL"
    waited=$((waited + POLL_INTERVAL))
done

if [ "$acknowledged" -ne 1 ]; then
    # A versao nova subiu e nao confirmou, ou nem subiu. Nos dois casos a versao
    # anterior e a que se sabe boa.
    rollback health-timeout
fi

# --- 8. recibo de sucesso, SOMENTE depois do ACK ---

write_receipt success ""
log "OK promoted $version"

# --- 9. poda: a versao atual e UMA anterior, para o rollback do proximo ciclo ---

if [ -n "$saved_current" ]; then
    for entry in "$versions_dir"/*; do
        [ -d "$entry" ] || continue
        entry_name=${entry##*/}
        if [ "$entry_name" != "$version" ] && [ "$entry_name" != "$saved_current" ]; then
            rm -rf "$entry" 2>/dev/null
        fi
    done
fi

cleanup
rm -f "$0" 2>/dev/null
