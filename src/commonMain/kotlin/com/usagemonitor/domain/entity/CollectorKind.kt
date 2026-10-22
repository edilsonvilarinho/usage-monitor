package com.usagemonitor.domain.entity

/**
 * Quem segura a trava de instância única de uma pasta de dados e, por isso, coleta
 * (#440): o app desktop ou o serviço sem tela. Os outros processos só leem.
 */
enum class CollectorKind {
    APP,
    SERVICE
}
