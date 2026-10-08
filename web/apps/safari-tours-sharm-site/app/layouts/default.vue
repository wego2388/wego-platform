<template>
  <div class="flex min-h-screen flex-col bg-sts-canvas text-sts-ink">
    <SiteHeader />
    <SiteBookingNotice />
    <div class="sts-content-atmosphere flex-1">
      <slot />
    </div>
    <SiteFooter />
  </div>
</template>
