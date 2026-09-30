<script setup lang="ts">
import { useDiscoveryCopy } from "../composables/useDiscoveryCopy";
import { siteCopy, siteEmail, whatsappPhone, whatsappUrl, categoryMeta } from "../content/locales";
import { useSiteLocale } from "../composables/useSiteLocale";
import { CATEGORY_ORDER } from "../composables/useCatalog";

const copy = useDiscoveryCopy();
const locale = useSiteLocale();
const year = new Date().getFullYear();
</script>

<template>
  <footer class="bg-sts-ocean text-white">
    <BrandSectionDivider tone="sand" />
    <div class="mx-auto max-w-7xl px-4 py-14 sm:px-6 lg:px-10">
      <div class="grid gap-10 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <BrandLogo inverse :height="40" />
          <p class="mt-4 max-w-xs text-sm leading-6 text-white/75">{{ copy.footer.tagline }}</p>
        </div>

        <div>
          <h2 class="text-xs font-bold tracking-[0.12em] text-sts-sand uppercase">{{ copy.footer.explore }}</h2>
          <ul class="mt-4 grid gap-2 text-sm">
            <li><NuxtLinkLocale to="/tours" class="text-white/75 hover:text-white">{{ copy.category.all }}</NuxtLinkLocale></li>
            <li v-for="cat in CATEGORY_ORDER" :key="cat">
              <NuxtLinkLocale :to="`/category/${categoryMeta[cat].slug}`" class="text-white/75 hover:text-white">
                {{ siteCopy[locale].categories[cat].name }}
              </NuxtLinkLocale>
            </li>
          </ul>
        </div>

        <div>
          <h2 class="text-xs font-bold tracking-[0.12em] text-sts-sand uppercase">{{ copy.footer.help }}</h2>
          <ul class="mt-4 grid gap-2 text-sm">
            <li><NuxtLinkLocale to="/my-booking" class="text-white/75 hover:text-white">{{ copy.footer.myBooking }}</NuxtLinkLocale></li>
            <li><NuxtLinkLocale to="/terms" class="text-white/75 hover:text-white">{{ copy.footer.cancellation }}</NuxtLinkLocale></li>
            <li><NuxtLinkLocale to="/contact" class="text-white/75 hover:text-white">{{ copy.footer.contact }}</NuxtLinkLocale></li>
          </ul>
        </div>

        <div>
          <h2 class="text-xs font-bold tracking-[0.12em] text-sts-sand uppercase">{{ copy.footer.company }}</h2>
          <ul class="mt-4 grid gap-2 text-sm">
            <li>
              <a :href="whatsappUrl" target="_blank" rel="noopener" class="inline-flex items-center gap-2 text-white/75 hover:text-white">
                <Icon name="lucide:message-circle" class="size-4" aria-hidden="true" />{{ copy.nav.whatsapp }}
              </a>
            </li>
            <li>
              <a :href="`tel:${whatsappPhone}`" class="inline-flex items-center gap-2 text-white/75 hover:text-white">
                <Icon name="lucide:phone" class="size-4" aria-hidden="true" /><bdi dir="ltr">{{ whatsappPhone }}</bdi>
              </a>
            </li>
            <li>
              <a :href="`mailto:${siteEmail}`" class="inline-flex items-center gap-2 text-white/75 hover:text-white">
                <Icon name="lucide:mail" class="size-4" aria-hidden="true" /><bdi dir="ltr">{{ siteEmail }}</bdi>
              </a>
            </li>
          </ul>
        </div>
      </div>

      <div class="mt-10 flex flex-col items-center justify-between gap-3 border-t border-white/15 pt-6 text-center text-sm text-white/60 sm:flex-row">
        <span>© {{ year }} Safari Tours Sharm. {{ copy.footer.rights }}</span>
        <span class="flex gap-4">
          <NuxtLinkLocale to="/privacy" class="hover:text-white">{{ copy.footer.privacy }}</NuxtLinkLocale>
          <NuxtLinkLocale to="/terms" class="hover:text-white">{{ copy.footer.terms }}</NuxtLinkLocale>
        </span>
      </div>
    </div>
  </footer>
</template>
