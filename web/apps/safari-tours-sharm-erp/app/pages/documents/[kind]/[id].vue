<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { WegoAlert } from "@wego/ui";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../../../composables/useAuthSession";
import {
  ToursApiError, printCancellationForm, printSettlementStatement, printDriverSheet, printPickupManifest, printSupplierOrder, printReceipt, printRunSheet, printVoucher,
  type CancellationFormDocument, type SettlementStatementDocument, type DriverSheetDocument, type PickupManifestDocument, type SupplierOrderDocument, type ReceiptDocument, type RunSheetDocument, type VoucherDocument,
} from "../../../composables/useToursApi";
import { useErpLocale } from "../../../composables/useErpLocale";
import ErpLanguageSwitch from "../../../components/ErpLanguageSwitch.vue";
import VoucherDoc from "../../../components/documents/VoucherDocument.vue";
import ReceiptDoc from "../../../components/documents/ReceiptDocument.vue";
import RunSheetDoc from "../../../components/documents/RunSheetDocument.vue";
import PickupManifestDoc from "../../../components/documents/PickupManifestDocument.vue";
import DriverSheetDoc from "../../../components/documents/DriverSheetDocument.vue";
import SupplierOrderDoc from "../../../components/documents/SupplierOrderDocument.vue";
import CancellationFormDoc from "../../../components/documents/CancellationFormDocument.vue";
import SettlementStatementDoc from "../../../components/documents/SettlementStatementDocument.vue";
import { docMessage, type DocumentLanguage, type DocumentMessageKey } from "../../../utils/documentMessages";
import { splitSupplierOrderSubject } from "../../../utils/opsRegistry";
import { DOCUMENT_KINDS, canPrintDocument, isValidSubject, type DocumentKind } from "../../../utils/documentFormat";
import { parseStatementSubject } from "../../../utils/financeOps";

definePageMeta({ layout: "print" });

/**
 * Print preview for one office document. Nothing is recorded until staff press
 * "Generate and print": that call records the print (original or reprint) on
 * the server and returns the data; the browser then prints this page. No PDF is
 * made or stored. The document language is chosen here and is independent of
 * the dashboard language.
 */
const route = useRoute();
const router = useRouter();
const { locale, direction } = useErpLocale();
const ui = (key: DocumentMessageKey, params?: Record<string, string | number>) => docMessage(locale.value, key, params);

const kind = computed(() => String(route.params.kind) as DocumentKind);
const subject = computed(() => String(route.params.id));
const valid = computed(() => (DOCUMENT_KINDS as readonly string[]).includes(kind.value) && isValidSubject(kind.value, subject.value));

const session = ref<AuthSession | null>(null);
const lang = ref<DocumentLanguage>(route.query.lang === "ar" ? "ar" : route.query.lang === "en" ? "en" : locale.value);
type AnyDoc = VoucherDocument | ReceiptDocument | RunSheetDocument | PickupManifestDocument | CancellationFormDocument | DriverSheetDocument | SupplierOrderDocument | SettlementStatementDocument;
const doc = ref<AnyDoc | null>(null);
const state = ref<"idle" | "loading" | "ready" | "error">("idle");
const errorKey = ref<DocumentMessageKey | null>(null);
const errorCode = ref("");
const sheet = ref<HTMLElement | null>(null);

const allowed = computed(() => canPrintDocument(kind.value, (p) => hasPermission(session.value, p)));
const kindTitleKey = computed<DocumentMessageKey>(() => ({
  voucher: "doc.voucher.title", receipt: "doc.receipt.title", cancellation: "doc.cancel.title", "run-sheet": "doc.run.title", pickup: "doc.pickup.title", "driver-sheet": "doc.drv.title", "supplier-order": "doc.sup.title", "settlement-statement": "doc.stl.title",
} as const)[kind.value] ?? "doc.voucher.title");
useHead(() => ({ title: doc.value ? `${docMessage(lang.value, kindTitleKey.value)} ${doc.value.document.number}` : ui(kindTitleKey.value) }));

const ERRORS: Record<string, DocumentMessageKey> = {
  booking_not_confirmed: "doc.ui.errorNotConfirmed",
  not_a_collection: "doc.ui.errorNotACollection",
  booking_not_cancelled: "doc.ui.errorNotCancelled",
  no_cash_collected: "doc.ui.errorNoCash",
  not_an_office_booking: "doc.ui.errorNotOffice",
  no_driver_assigned: "doc.ui.errorNoDriver",
  supplier_not_assigned: "doc.ui.errorSupplierNotAssigned",
};

async function request(token: string): Promise<AnyDoc> {
  switch (kind.value) {
    case "voucher": return printVoucher(token, subject.value, lang.value);
    case "receipt": return printReceipt(token, subject.value, lang.value);
    case "cancellation": return printCancellationForm(token, subject.value, lang.value);
    case "run-sheet": return printRunSheet(token, subject.value, lang.value);
    case "driver-sheet": return printDriverSheet(token, subject.value, lang.value);
    case "supplier-order": {
      const [slotId, supplierId] = splitSupplierOrderSubject(subject.value) ?? ["", ""];
      return printSupplierOrder(token, slotId, supplierId, lang.value);
    }
    case "settlement-statement": {
      const parsed = parseStatementSubject(subject.value);
      if (!parsed) throw new ToursApiError(400, "invalid_statement_link");
      return printSettlementStatement(token, parsed.party, parsed.id, parsed.from, parsed.to, lang.value);
    }
    default: return printPickupManifest(token, subject.value, lang.value);
  }
}

async function generate() {
  if (!session.value || !valid.value || !allowed.value || state.value === "loading") return;
  state.value = "loading";
  errorKey.value = null;
  try {
    doc.value = await request(session.value.token);
    state.value = "ready";
    await nextTick();
    sheet.value?.focus();
    if (typeof window.print === "function") window.print();
  } catch (err) {
    doc.value = null;
    state.value = "error";
    if (err instanceof ToursApiError && err.status === 401) {
      clearAuthSession();
      void router.replace("/login");
      return;
    }
    if (err instanceof ToursApiError && err.status === 403) errorKey.value = "doc.ui.errorForbidden";
    else if (err instanceof ToursApiError && err.status === 404) errorKey.value = "doc.ui.errorNotFound";
    else if (err instanceof ToursApiError && ERRORS[err.errorCode]) errorKey.value = ERRORS[err.errorCode]!;
    else {
      errorKey.value = "doc.ui.errorFailed";
      errorCode.value = err instanceof ToursApiError ? err.errorCode : "network";
    }
  }
}

function printAgain() {
  if (typeof window.print === "function") window.print();
}

/** A different language is a different print: clear the preview so the next press records it. */
watch(lang, () => {
  doc.value = null;
  state.value = "idle";
  errorKey.value = null;
});

function back() {
  if (window.history.length > 1) router.back();
  else void router.push("/");
}

onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) void router.replace("/login");
});
</script>

<template>
  <main class="doc-page" :lang="locale" :dir="direction">
    <div class="doc-toolbar print:hidden">
      <div class="mx-auto flex max-w-[210mm] flex-wrap items-center gap-3 px-4 py-3">
        <button type="button" class="rounded-lg border border-white/30 px-3 py-1.5 text-sm font-semibold hover:bg-white/10" @click="back">
          <span aria-hidden="true" class="inline-block rtl:rotate-180">←</span> {{ ui("doc.ui.back") }}
        </button>
        <h1 class="me-auto text-base font-semibold">{{ ui(kindTitleKey) }}</h1>
        <ErpLanguageSwitch />
      </div>
    </div>

    <div class="mx-auto max-w-[210mm] px-4 py-4 print:hidden">
      <WegoAlert v-if="!valid" variant="danger" role="alert">{{ ui("doc.ui.errorBadLink") }}</WegoAlert>
      <WegoAlert v-else-if="session && !allowed" variant="danger" role="alert">{{ ui("doc.ui.errorForbidden") }}</WegoAlert>
      <template v-else>
        <fieldset class="flex flex-wrap items-center gap-3 rounded-xl border border-sts-border bg-sts-surface px-4 py-3">
          <legend class="px-1 text-sm font-semibold">{{ ui("doc.ui.language") }}</legend>
          <label class="inline-flex items-center gap-2 text-sm"><input v-model="lang" type="radio" name="doc-language" value="en"> English</label>
          <label class="inline-flex items-center gap-2 text-sm"><input v-model="lang" type="radio" name="doc-language" value="ar"> <span lang="ar">العربية</span></label>
          <button
            type="button" class="ms-auto rounded-lg bg-sts-ocean px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
            :disabled="state === 'loading' || !session" @click="generate"
          >{{ state === "loading" ? ui("doc.ui.loading") : ui("doc.ui.generate") }}</button>
          <button v-if="state === 'ready'" type="button" class="rounded-lg border border-sts-border bg-sts-surface px-3 py-2 text-sm font-semibold" @click="printAgain">{{ ui("doc.ui.printAgain") }}</button>
        </fieldset>
        <p class="mt-2 text-xs text-sts-muted">{{ ui("doc.ui.recordNote") }}</p>
        <p v-if="state === 'ready' && doc" class="mt-2 text-sm font-semibold text-sts-success" role="status">
          {{ ui("doc.ui.recorded", { number: doc.document.number, version: doc.document.version }) }}
        </p>
        <WegoAlert v-if="state === 'error' && errorKey" variant="danger" class="mt-3" role="alert">{{ ui(errorKey, { code: errorCode }) }}</WegoAlert>
        <p v-if="state === 'loading'" class="mt-2 text-sm text-sts-muted" role="status">{{ ui("doc.ui.loading") }}</p>
      </template>
    </div>

    <section v-if="doc" ref="sheet" tabindex="-1" class="doc-stage" :aria-label="ui('doc.ui.preview')">
      <VoucherDoc v-if="kind === 'voucher'" :doc="doc as VoucherDocument" :lang="lang" />
      <ReceiptDoc v-else-if="kind === 'receipt'" :doc="doc as ReceiptDocument" :lang="lang" />
      <CancellationFormDoc v-else-if="kind === 'cancellation'" :doc="doc as CancellationFormDocument" :lang="lang" />
      <RunSheetDoc v-else-if="kind === 'run-sheet'" :doc="doc as RunSheetDocument" :lang="lang" />
      <DriverSheetDoc v-else-if="kind === 'driver-sheet'" :doc="doc as DriverSheetDocument" :lang="lang" />
      <SupplierOrderDoc v-else-if="kind === 'supplier-order'" :doc="doc as SupplierOrderDocument" :lang="lang" />
      <SettlementStatementDoc v-else-if="kind === 'settlement-statement'" :doc="doc as SettlementStatementDocument" :lang="lang" />
      <PickupManifestDoc v-else :doc="doc as PickupManifestDocument" :lang="lang" />
    </section>
  </main>
</template>

<style>
.doc-toolbar { background: var(--color-sts-ocean, #0a2342); color: #fff; }
.doc-stage { padding: 0 0 16px; outline: none; }
@media print {
  .doc-page, .doc-stage { padding: 0; background: #fff; }
}
</style>
