package com.wego.travelmarketplace.domain

/**
 * `SERVICE_OWNERSHIP.md`: "`DIRECT` or `PARTNER`; avoid `MIXED` at launch."
 * `DIRECT` means Sharm To Go itself is the fulfilment owner — [Service.providerId]
 * must be null. `PARTNER` means another business is — `providerId` is then
 * required, and the public page must show that provider's "Operated by" label.
 */
enum class FulfilmentModel {
    DIRECT,
    PARTNER,
}
