package com.wego.toursoperator.application

import com.wego.events.OutboxWriter
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.CustomerContact
import com.wego.toursoperator.domain.TourSlotId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import tools.jackson.databind.ObjectMapper
import java.time.Clock

class EnquiryModeTest {
    @Test
    fun `creating an enquiry never opens a transaction or touches inventory PII audit or outbox`() {
        val tours = mock(TourRepository::class.java)
        val slots = mock(TourSlotRepository::class.java)
        val bookings = mock(BookingRepository::class.java)
        val audit = mock(BookingAuditRecorder::class.java)
        val sales = mock(SalesControlRepository::class.java)
        val outbox = mock(OutboxWriter::class.java)
        val tx = mock(TransactionRunner::class.java)
        val service =
            CreateBookingService(
                tours,
                slots,
                bookings,
                audit,
                sales,
                outbox,
                tx,
                ObjectMapper(),
                Clock.systemUTC(),
                BookingMode.ENQUIRY_ONLY,
            )
        val command =
            CreateBookingCommand(
                slotId = TourSlotId.generate(),
                adultsCount = 1,
                childrenCount = 0,
                customer = CustomerContact("Test", "+201000000000", "EG", null),
                hotelName = "Test hotel",
                hotelRoom = null,
                specialRequests = null,
                locale = "en",
                actorUserId = null,
                correlationId = null,
            )
        assertThat(service.create(command)).isEqualTo(CreateBookingResult.OnlineBookingUnavailable)
        verifyNoInteractions(tours, slots, bookings, audit, sales, outbox, tx)
    }

    @Test
    fun `initiate or resume refuses before database access writes or provider call`() {
        val bookings = mock(BookingRepository::class.java)
        val payments = mock(PaymentRepository::class.java)
        val provider = mock(PaymobClient::class.java)
        val sales = mock(SalesControlRepository::class.java)
        val tx = mock(TransactionRunner::class.java)
        val service = InitiatePaymentService(bookings, payments, provider, sales, tx, Clock.systemUTC(), BookingMode.ENQUIRY_ONLY)
        assertThat(
            service.initiate(InitiatePaymentCommand(BookingId.generate(), null)),
        ).isEqualTo(InitiatePaymentResult.OnlinePaymentUnavailable)
        verifyNoInteractions(bookings, payments, provider, sales, tx)
    }
}
