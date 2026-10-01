import { buildRobots } from "../utils/sitemap";

export default defineEventHandler((event) => {
  const config = useRuntimeConfig(event);
  const baseUrl = (config.public.i18n as { baseUrl?: string } | undefined)?.baseUrl ?? "http://localhost:3000";
  setResponseHeader(event, "Content-Type", "text/plain; charset=utf-8");
  setResponseHeader(event, "Cache-Control", "public, max-age=3600");
  return buildRobots(baseUrl);
});
