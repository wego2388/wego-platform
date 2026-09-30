import { defineEventHandler, getCookie, getRequestURL, sendRedirect, setResponseHeader } from "h3";
import { localeRedirectTarget } from "../utils/localeRedirect";

/** Redirects language-less URLs to the visitor's locale (see localeRedirectTarget). */
export default defineEventHandler((event) => {
  const url = getRequestURL(event);
  const target = localeRedirectTarget(url.pathname, url.search, getCookie(event, "sts_locale"));
  if (!target) return;
  // The target depends on the visitor's cookie: never let a shared cache store it.
  setResponseHeader(event, "Cache-Control", "no-store");
  return sendRedirect(event, target, 302);
});
