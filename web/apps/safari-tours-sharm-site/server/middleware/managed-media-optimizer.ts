import { isManagedOptimizerRequest } from "../../app/utils/managedMedia";

export default defineEventHandler((event) => {
  if (!isManagedOptimizerRequest(getRequestURL(event).pathname)) return;
  setHeader(event, "Cache-Control", "no-store, private");
  throw createError({ statusCode: 404, statusMessage: "Not found" });
});
