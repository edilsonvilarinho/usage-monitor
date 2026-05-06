package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeepSeekBalanceResponse(
    @SerialName("is_available") val isAvailable: Boolean,
    @SerialName("balance_infos") val balanceInfos: List<DeepSeekBalanceInfoDto>
)

@Serializable
data class DeepSeekBalanceInfoDto(
    @SerialName("currency") val currency: String,
    @SerialName("total_balance") val totalBalance: String,
    @SerialName("granted_balance") val grantedBalance: String,
    @SerialName("topped_up_balance") val toppedUpBalance: String
)
