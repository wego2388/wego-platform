<script setup lang="ts">
import { computed } from "vue";
import { useDiscoveryCopy } from "../composables/useDiscoveryCopy";
import { siteCopy, siteEmail, whatsappPhone, whatsappUrl, categoryMeta, instagramUrl, facebookUrl } from "../content/locales";
import { infoCopy } from "../content/info";
import { useConsent } from "../composables/useConsent";
import { useSiteLocale } from "../composables/useSiteLocale";
import { CATEGORY_ORDER } from "../composables/useCatalog";

const copy = useDiscoveryCopy();
const locale = useSiteLocale();
const year = new Date().getFullYear();
const info = computed(() => infoCopy[locale.value]);
const consent = useConsent();
/** Forget the choice and reload, so already-loaded tags are gone and the banner asks again. */
function changeCookies() {
  consent.reset();
  window.location.reload();
}
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
            <li><NuxtLinkLocale to="/trip-finder" class="text-white/75 hover:text-white">{{ info.finder.title }}</NuxtLinkLocale></li>
            <li><NuxtLinkLocale to="/faq" class="text-white/75 hover:text-white">{{ info.faq.title }}</NuxtLinkLocale></li>
            <li><NuxtLinkLocale to="/terms#section-4" class="text-white/75 hover:text-white">{{ copy.footer.cancellation }}</NuxtLinkLocale></li>
            <li><NuxtLinkLocale to="/about" class="text-white/75 hover:text-white">{{ info.about.title }}</NuxtLinkLocale></li>
            <li><NuxtLinkLocale to="/contact" class="text-white/75 hover:text-white">{{ copy.footer.contact }}</NuxtLinkLocale></li>
          </ul>
        </div>

        <div>
          <h2 class="text-xs font-bold tracking-[0.12em] text-sts-sand uppercase">{{ copy.footer.company }}</h2>
          <ul class="mt-4 grid gap-2 text-sm">
            <li>
              <a :href="instagramUrl" target="_blank" rel="noopener" class="inline-flex items-center gap-2 text-white/75 hover:text-white">
                <Icon name="lucide:instagram" class="size-4" aria-hidden="true" />Instagram
              </a>
            </li>
            <li>
              <a :href="facebookUrl" target="_blank" rel="noopener" class="inline-flex items-center gap-2 text-white/75 hover:text-white">
                <Icon name="lucide:facebook" class="size-4" aria-hidden="true" />Facebook
              </a>
            </li>
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
          <button v-if="consent.configured.value" type="button" class="hover:text-white" @click="changeCookies">{{ info.consent.title }}</button>
        </span>
      </div>
    </div>
  </footer>
</template>
