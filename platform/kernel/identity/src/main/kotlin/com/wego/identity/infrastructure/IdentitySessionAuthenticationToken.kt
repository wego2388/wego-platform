package com.wego.identity.infrastructure

import com.wego.identity.application.AuthenticatedPrincipal
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

class IdentitySessionAuthenticationToken(
    private val authenticatedPrincipal: AuthenticatedPrincipal,
) : AbstractAuthenticationToken(
        authenticatedPrincipal.permissions.map { SimpleGrantedAuthority(it.value) },
    ) {
    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null

    override fun getPrincipal(): AuthenticatedPrincipal = authenticatedPrincipal
}
