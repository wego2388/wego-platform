# Wego Travel Marketplace

This product boundary owns reusable travel-marketplace behavior. Sharm To Go is
its first client configuration; it is not the product itself.

The current packet establishes only the compiled Spring Modulith marker and the
versioned product manifest. Catalog, provider, availability, request,
confirmation, payment, settlement, content, and verified-review domains are
specified in `clients/sharm-to-go/PRODUCT_BLUEPRINT.md` but deliberately do not
exist as runtime modules or database tables yet. They are introduced one
vertical slice at a time after the owner approves real service rules.

Product rules must never contain Sharm To Go branding, client phone numbers,
provider lists, prices, translations, or secrets. Those belong to client
configuration or operational data in an isolated client deployment.
