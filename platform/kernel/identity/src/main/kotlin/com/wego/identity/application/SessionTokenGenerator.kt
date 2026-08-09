package com.wego.identity.application

interface SessionTokenGenerator {
    fun generate(): String

    fun hash(rawToken: String): String
}
