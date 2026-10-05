package com.wego.toursoperator.infrastructure

import com.wego.toursoperator.application.PaymobCheckoutCommand
import com.wego.toursoperator.application.PaymobCheckoutResult
import com.wego.toursoperator.application.PaymobClient
import com.wego.toursoperator.application.PaymobRefundResult

/** Fresh enquiry-only installations. Never simulates successful payment or refund. */
class DisabledPaymobClient : PaymobClient {
    override val unavailable: Boolean = true

    override fun createCheckout(command: PaymobCheckoutCommand): PaymobCheckoutResult =
        PaymobCheckoutResult.Failure("payment_provider_unavailable")

    override fun buildCheckoutUrl(checkoutToken: String): String = error("payment_provider_unavailable")

    override fun acceptsWebhookIdentity(
        integrationId: String,
        ownerId: String,
    ): Boolean = false

    override fun verifyWebhookSignature(
        payload: Map<String, String>,
        receivedHmac: String,
    ): Boolean = false

    override fun refund(
        paymobTransactionId: String,
        amountCents: Long,
    ): PaymobRefundResult = PaymobRefundResult.Failure("payment_provider_unavailable")
}
