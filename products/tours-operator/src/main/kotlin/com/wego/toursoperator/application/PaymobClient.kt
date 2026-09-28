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
     * Creates a Paymob order for the given booking.
     * merchantRefNumber must be unique per booking (we use bookingId).
     */
    fun createOrder(
        merchantRefNumber: String,
        amountCents: Long,
        currencyCode: String,
        items: List<PaymobOrderItem>,
    ): PaymobOrderResult

    /**
     * Builds the hosted-checkout URL for an existing Paymob order.
     * No network call — purely constructs the redirect URL.
     */
    fun buildCheckoutUrl(paymobOrderId: String): String

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

data class PaymobOrderItem(
    val name: String,
    val amountCents: Long,
    val quantity: Int,
)

sealed class PaymobOrderResult {
    data class Success(val orderId: String) : PaymobOrderResult()
    data class Failure(val message: String) : PaymobOrderResult()
}

sealed class PaymobRefundResult {
    data object Success : PaymobRefundResult()
    data class Failure(val message: String) : PaymobRefundResult()
}
