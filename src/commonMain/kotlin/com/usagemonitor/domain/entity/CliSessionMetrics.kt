package com.usagemonitor.domain.entity

/**
 * O que a linha de status do modo terminal (#439) mostra da sessão do Claude Code em
 * curso: o custo que o índice da #381 já calculou dos tokens, pela tabela de preços, e a
 * vazão de saída. [unpricedTurns] maior que zero quer dizer custo parcial (modelo sem
 * tarifa), mostrado com `+`; [throughput] nulo quer dizer não medido.
 */
data class CliSessionMetrics(
    val costMicros: Long,
    val unpricedTurns: Int,
    val throughput: OutputThroughput?
)
