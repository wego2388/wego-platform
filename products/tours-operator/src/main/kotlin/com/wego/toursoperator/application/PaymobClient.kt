package com.wego.toursoperator.application

/**
 * Port (interface) for the Paymob payment gateway.
 * The real implementation lives in infrastructure and uses HTTP.
 * A stub/fake is used in tests.
 *
 * All amounts are in minor units (EUR cents).
 */
interface PaymobClient {
    /**
     * Creates a Paymob Intention and returns an opaque client secret for the
     * provider-hosted Unified Checkout.
     */
    fun createCheckout(command: PaymobCheckoutCommand): PaymobCheckoutResult

    /** Builds a provider-hosted checkout URL from the opaque client secret. */
    fun buildCheckoutUrl(checkoutToken: String): String

    /** True only for the configured merchant account and integration. */
    fun acceptsWebhookIdentity(
        integrationId: String,
        ownerId: String,
    ): Boolean

    /**
     * Verifies the HMAC-SHA512 signature on an incoming webhook/callback.
     * Returns true if the signature is valid.
     */
    fun verifyWebhookSignature(
        payload: Map<String, String>,
        receivedHmac: String,
    ): Boolean

    /**
     * Issues a refund for the given transaction.
     */
    fun refund(
        paymobTransactionId: String,
        amountCents: Long,
    ): PaymobRefundResult
}

data class PaymobCheckoutCommand(
    val merchantRefNumber: String,
    val amountCents: Long,
    val currencyCode: String,
    val items: List<PaymobOrderItem>,
    val billing: PaymobBillingData,
)

data class PaymobBillingData(
    val fullName: String,
    val phone: String,
    val email: String?,
)

data class PaymobOrderItem(
    val name: String,
    val amountCents: Long,
    val quantity: Int,
)

sealed class PaymobCheckoutResult {
    data class Success(
        val orderId: String,
        val checkoutToken: String,
    ) : PaymobCheckoutResult()

    data class Failure(
        val message: String,
    ) : PaymobCheckoutResult()
}

sealed class PaymobRefundResult {
    data object Success : PaymobRefundResult()

    data class Failure(
        val message: String,
    ) : PaymobRefundResult()
}
