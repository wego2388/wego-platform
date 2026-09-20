package com.wego.mobile.sharmtogo.content

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlEncodingTest {
    @Test
    fun `leaves unreserved characters alone`() {
        assertEquals("Sharm-To_Go.1~", percentEncode("Sharm-To_Go.1~"))
    }

    @Test
    fun `encodes spaces and punctuation`() {
        assertEquals("Hello%20Sharm%20To%20Go%3F", percentEncode("Hello Sharm To Go?"))
    }

    @Test
    fun `encodes Arabic as UTF-8 bytes`() {
        assertEquals("%D9%85%D8%B1%D8%AD%D8%A8%D8%A7", percentEncode("مرحبا"))
    }
}
