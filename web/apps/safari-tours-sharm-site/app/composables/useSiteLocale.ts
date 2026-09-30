import { computed, type WritableComputedRef } from "vue";
import type { StsLocale } from "../content/locales";

const SUPPORTED: StsLocale[] = ["en", "ru", "ar", "it"];

function isSupported(v: string): v is StsLocale {
  return (SUPPORTED as string[]).includes(v);
}

/**
 * The active locale comes from the URL prefix (/en, /ar, /ru, /it), so the
 * server renders the right language, `lang` and `dir` on the first response.
 *
 * Assigning a new locale navigates to the same page in that language and
 * remembers the choice in the `sts_locale` cookie, which the root redirect
 * and the language-less URL redirect both honour.
 */
export function useSiteLocale(): WritableComputedRef<StsLocale> {
  const { locale } = useI18n();
  const switchLocalePath = useSwitchLocalePath();
  const remembered = useCookie<string | null>("sts_locale", { maxAge: 60 * 60 * 24 * 365, sameSite: "lax" });

  return computed({
    get: () => (isSupported(locale.value) ? locale.value : "en"),
    set: (next: StsLocale) => {
      if (!isSupported(next) || next === locale.value) return;
      remembered.value = next;
      void navigateTo(switchLocalePath(next));
    },
  });
}
