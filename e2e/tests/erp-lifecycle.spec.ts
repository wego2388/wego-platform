import { expect, test } from "@playwright/test";
import { E2E_STAFF_EMAIL, E2E_STAFF_PASSWORD } from "../seed.mjs";

const OFFERING_TITLE = "E2E Lifecycle Trip";
const OFFERING_STARTS_ON = "2027-01-01";
const CUSTOMER_NAME = "E2E Test Diver";

test.describe("ERP diving bookings lifecycle", () => {
  test("login, create offering, create booking, paginate, mark paid, cancel with reason, refund with reason, logout", async ({
    page,
  }) => {
    // window.confirm gates offering-close/booking-cancel/booking-refund —
    // accept every confirmation dialog for the rest of this test.
    page.on("dialog", (dialog) => dialog.accept());

    await test.step("login", async () => {
      await page.goto("/login", { waitUntil: "networkidle" });
      await page.locator("#email").fill(E2E_STAFF_EMAIL);
      await page.locator("#password").fill(E2E_STAFF_PASSWORD);
      await page.getByRole("button", { name: "Sign in" }).click();
      await expect(page.getByText(`Signed in as ${E2E_STAFF_EMAIL}`)).toBeVisible();
    });

    await test.step("create offering", async () => {
      await page.getByRole("link", { name: "Offerings" }).click();
      await expect(page).toHaveURL(/\/offerings$/);

      await page.locator("#title").fill(OFFERING_TITLE);
      await page.locator("#startsOn").fill(OFFERING_STARTS_ON);
      await page.locator("#amount").fill("45.00");
      await page.getByRole("button", { name: "Create offering" }).click();

      await expect(page.getByText(OFFERING_TITLE)).toBeVisible();
    });

    await test.step("pagination reaches the offering created past the first full page", async () => {
      // A fresh load re-fetches page 0 from the server (not the optimistic,
      // client-prepended list from the step above) — 50 seeded, older
      // offerings sort first, pushing this one to page 2.
      await page.reload();
      await expect(page.getByText("Page 1")).toBeVisible();
      await expect(page.getByText(OFFERING_TITLE)).not.toBeVisible();

      const nextButton = page.getByRole("button", { name: "Next" });
      await expect(nextButton).toBeEnabled();
      await nextButton.click();

      await expect(page.getByText("Page 2")).toBeVisible();
      await expect(page.getByText(OFFERING_TITLE)).toBeVisible();
      await expect(page.getByRole("button", { name: "Previous" })).toBeEnabled();
    });

    const bookingRow = page.locator("li", { hasText: CUSTOMER_NAME });

    await test.step("create booking", async () => {
      // offerings.vue has no in-page nav back to /bookings (only the
      // index/login success panels do) — a direct navigation is the real
      // path a bookmark or address-bar entry would take too.
      await page.goto("/bookings", { waitUntil: "networkidle" });
      await expect(page).toHaveURL(/\/bookings$/);

      await page.locator("#offeringId").selectOption({ label: `${OFFERING_TITLE} — ${OFFERING_STARTS_ON} (45.00 EUR)` });
      await page.locator("#partySize").fill("2");
      await page.locator("#customerName").fill(CUSTOMER_NAME);
      await page.locator("#customerEmail").fill("e2e-diver@example.com");
      await page.getByRole("button", { name: "Create booking" }).click();

      // Scoped to the booking row, not the whole page — the create form's
      // now-hidden <select><option> above still contains this same offering
      // label text and would otherwise make this assertion ambiguous.
      await expect(bookingRow.getByText(`${CUSTOMER_NAME} · 2 pax`)).toBeVisible();
      await expect(bookingRow.getByText(`${OFFERING_TITLE} — ${OFFERING_STARTS_ON}`)).toBeVisible();
      await expect(bookingRow.getByText("unit 45.00 EUR × 2 = total 90.00 EUR")).toBeVisible();
    });

    await test.step("mark paid", async () => {
      await bookingRow.getByRole("button", { name: "Mark paid" }).click();
      await expect(bookingRow.getByText("payment PAID")).toBeVisible();
    });

    await test.step("cancel with a reason", async () => {
      await bookingRow.locator('[id^="cancel-reason-"]').fill("E2E test cancellation");
      await bookingRow.getByRole("button", { name: "Cancel" }).click();
      await expect(bookingRow.getByText("CANCELLED (E2E test cancellation)")).toBeVisible();
    });

    await test.step("refund with a reason", async () => {
      await bookingRow.locator('[id^="refund-reason-"]').fill("E2E test refund");
      await bookingRow.getByRole("button", { name: "Refund" }).click();
      await expect(bookingRow.getByText("payment REFUNDED")).toBeVisible();
    });

    await test.step("logout ends the session", async () => {
      // login.vue rehydrates its signed-in panel from the stored session on
      // mount, so returning to /login while still authenticated shows
      // "Signed in as ..." directly — no need to re-enter credentials.
      await page.goto("/login", { waitUntil: "networkidle" });
      await expect(page.getByText(`Signed in as ${E2E_STAFF_EMAIL}`)).toBeVisible();

      await page.getByRole("button", { name: "Sign out" }).click();

      await page.goto("/offerings", { waitUntil: "networkidle" });
      await expect(page.getByText("You need to sign in to view offerings.")).toBeVisible();
    });
  });
});

const EMPLOYEE_NAME = "E2E Lifecycle Employee";

test.describe("ERP HR employee lifecycle", () => {
  test("login, create employee, roster omits salary, edit reveals salary, terminate removes from active list", async ({ page }) => {
    page.on("dialog", (dialog) => dialog.accept());

    await test.step("login", async () => {
      await page.goto("/login", { waitUntil: "networkidle" });
      await page.locator("#email").fill(E2E_STAFF_EMAIL);
      await page.locator("#password").fill(E2E_STAFF_PASSWORD);
      await page.getByRole("button", { name: "Sign in" }).click();
      await expect(page.getByText(`Signed in as ${E2E_STAFF_EMAIL}`)).toBeVisible();
    });

    await test.step("create employee", async () => {
      // login.vue's signed-in panel only links to Offerings/Bookings (the
      // two modules that existed when it was first written) — same as
      // Divers/Equipment/Accounts/Roles, a direct navigation is the real
      // path a bookmark or address-bar entry would take too.
      await page.goto("/employees", { waitUntil: "networkidle" });
      await expect(page).toHaveURL(/\/employees$/);

      await page.locator("#fullName").fill(EMPLOYEE_NAME);
      await page.locator("#position").fill("Dive Instructor");
      await page.locator("#hireDate").fill("2026-01-01");
      await page.locator("#amount").fill("15000.00");
      await page.locator("#currencyCode").fill("EGP");
      await page.getByRole("button", { name: "Create employee" }).click();

      await expect(page.getByText(EMPLOYEE_NAME)).toBeVisible();
    });

    const employeeRow = page.locator("li", { hasText: EMPLOYEE_NAME });

    await test.step("roster omits salary — a fresh load re-fetches the summary projection", async () => {
      await page.reload();
      await expect(employeeRow.getByText("Dive Instructor")).toBeVisible();
      await expect(page.getByText("15000.00")).not.toBeVisible();
    });

    await test.step("edit fetches the full record, revealing the salary", async () => {
      await employeeRow.getByRole("button", { name: "Edit" }).click();
      await expect(page.locator("#amount")).toHaveValue("15000.00");
      await page.getByRole("button", { name: "Cancel" }).click();
    });

    await test.step("terminate removes the employee from the active list", async () => {
      await employeeRow.locator('[id^="terminate-reason-"]').fill("E2E test termination");
      await employeeRow.getByRole("button", { name: "Terminate" }).click();
      await expect(page.getByText(EMPLOYEE_NAME)).not.toBeVisible();
    });

    await test.step("logout ends the session", async () => {
      await page.goto("/login", { waitUntil: "networkidle" });
      await expect(page.getByText(`Signed in as ${E2E_STAFF_EMAIL}`)).toBeVisible();

      await page.getByRole("button", { name: "Sign out" }).click();

      await page.goto("/employees", { waitUntil: "networkidle" });
      await expect(page.getByText("You need to sign in to view employee records.")).toBeVisible();
    });
  });
});

const ATTENDANCE_EMPLOYEE_NAME = "E2E Attendance Employee";

test.describe("ERP HR attendance and leave lifecycle", () => {
  test("login, create employee, correct attendance for the same day, submit and approve a leave request", async ({ page }) => {
    page.on("dialog", (dialog) => dialog.accept());

    await test.step("login", async () => {
      await page.goto("/login", { waitUntil: "networkidle" });
      await page.locator("#email").fill(E2E_STAFF_EMAIL);
      await page.locator("#password").fill(E2E_STAFF_PASSWORD);
      await page.getByRole("button", { name: "Sign in" }).click();
      await expect(page.getByText(`Signed in as ${E2E_STAFF_EMAIL}`)).toBeVisible();
    });

    await test.step("create an employee to attach records to", async () => {
      await page.goto("/employees", { waitUntil: "networkidle" });
      await page.locator("#fullName").fill(ATTENDANCE_EMPLOYEE_NAME);
      await page.locator("#position").fill("Front Desk");
      await page.locator("#hireDate").fill("2026-01-01");
      await page.getByRole("button", { name: "Create employee" }).click();
      await expect(page.getByText(ATTENDANCE_EMPLOYEE_NAME)).toBeVisible();
    });

    await test.step("recording attendance twice for the same day corrects it, not duplicates it", async () => {
      await page.goto("/attendance", { waitUntil: "networkidle" });
      await page.locator("#employeeId").selectOption({ label: ATTENDANCE_EMPLOYEE_NAME });
      await page.locator("#attendanceDate").fill("2026-08-30");
      await page.locator("#status").selectOption("LATE");
      await page.locator("#notes").fill("Traffic");
      await page.getByRole("button", { name: "Record attendance" }).click();
      await expect(page.getByText("Traffic")).toBeVisible();

      await page.locator("#employeeId").selectOption({ label: ATTENDANCE_EMPLOYEE_NAME });
      await page.locator("#attendanceDate").fill("2026-08-30");
      await page.locator("#status").selectOption("PRESENT");
      await page.locator("#notes").fill("Actually on time");
      await page.getByRole("button", { name: "Record attendance" }).click();

      await page.reload();
      await expect(page.getByText("Actually on time")).toBeVisible();
      await expect(page.getByText("Traffic")).not.toBeVisible();
    });

    await test.step("submit a leave request and approve it", async () => {
      await page.goto("/leave-requests", { waitUntil: "networkidle" });
      await page.locator("#employeeId").selectOption({ label: ATTENDANCE_EMPLOYEE_NAME });
      await page.locator("#startDate").fill("2026-09-01");
      await page.locator("#endDate").fill("2026-09-05");
      await page.locator("#reason").fill("E2E leave reason");
      await page.getByRole("button", { name: "Submit request" }).click();
      await expect(page.getByText("E2E leave reason")).toBeVisible();

      const requestRow = page.locator("li", { hasText: "E2E leave reason" });
      await requestRow.getByRole("button", { name: "Approve" }).click();
      // The default filter is PENDING, so an approved request disappears from view.
      await expect(page.getByText("E2E leave reason")).not.toBeVisible();
    });

    await test.step("logout ends the session", async () => {
      await page.goto("/login", { waitUntil: "networkidle" });
      await expect(page.getByText(`Signed in as ${E2E_STAFF_EMAIL}`)).toBeVisible();

      await page.getByRole("button", { name: "Sign out" }).click();

      await page.goto("/attendance", { waitUntil: "networkidle" });
      await expect(page.getByText("You need to sign in to view attendance records.")).toBeVisible();
    });
  });
});

const CASH_ACCOUNT_CODE = "9910";
const REVENUE_ACCOUNT_CODE = "9920";

test.describe("ERP accounting lifecycle", () => {
  test("login, real seeded starter chart of accounts, create accounts, post a balanced entry, reverse it", async ({ page }) => {
    page.on("dialog", (dialog) => dialog.accept());

    await test.step("login", async () => {
      await page.goto("/login", { waitUntil: "networkidle" });
      await page.locator("#email").fill(E2E_STAFF_EMAIL);
      await page.locator("#password").fill(E2E_STAFF_PASSWORD);
      await page.getByRole("button", { name: "Sign in" }).click();
      await expect(page.getByText(`Signed in as ${E2E_STAFF_EMAIL}`)).toBeVisible();
    });

    await test.step("the real seeded starter chart of accounts is visible", async () => {
      await page.goto("/chart-of-accounts", { waitUntil: "networkidle" });
      await expect(page.getByText("1000 · Cash on Hand")).toBeVisible();
      await expect(page.getByText("4000 · Service Revenue")).toBeVisible();
    });

    await test.step("create two real accounts for this test", async () => {
      await page.locator("#code").fill(CASH_ACCOUNT_CODE);
      await page.locator("#name").fill("E2E Test Cash");
      await page.locator("#accountType").selectOption("ASSET");
      await page.getByRole("button", { name: "Create account" }).click();
      await expect(page.getByText(`${CASH_ACCOUNT_CODE} · E2E Test Cash`)).toBeVisible();

      await page.locator("#code").fill(REVENUE_ACCOUNT_CODE);
      await page.locator("#name").fill("E2E Test Revenue");
      await page.locator("#accountType").selectOption("REVENUE");
      await page.getByRole("button", { name: "Create account" }).click();
      await expect(page.getByText(`${REVENUE_ACCOUNT_CODE} · E2E Test Revenue`)).toBeVisible();
    });

    await test.step("post a real balanced journal entry", async () => {
      await page.goto("/journal-entries", { waitUntil: "networkidle" });
      await page.locator("#entryDate").fill("2026-08-31");
      await page.locator("#description").fill("E2E test booking revenue");
      await page.locator("#line-account-0").selectOption({ label: `${CASH_ACCOUNT_CODE} · E2E Test Cash` });
      await page.locator("#line-amount-0").fill("500.00");
      await page.locator("#line-account-1").selectOption({ label: `${REVENUE_ACCOUNT_CODE} · E2E Test Revenue` });
      await page.locator("#line-direction-1").selectOption("CREDIT");
      await page.locator("#line-amount-1").fill("500.00");
      await page.getByRole("button", { name: "Post entry" }).click();

      await expect(page.getByText("E2E test booking revenue")).toBeVisible();
      const entryRow = page.locator("li", { hasText: "E2E test booking revenue" });
      await expect(entryRow.getByText("500.00 EGP debit / 500.00 EGP credit")).toBeVisible();
    });

    await test.step("reverse the entry, flipping every line", async () => {
      const entryRow = page.locator("li", { hasText: "E2E test booking revenue" }).first();
      await entryRow.locator('[id^="reverse-reason-"]').fill("E2E test reversal");
      await entryRow.getByRole("button", { name: "Reverse" }).click();

      await page.reload();
      await expect(page.getByText("reverses another entry")).toBeVisible();
    });

    await test.step("logout ends the session", async () => {
      await page.goto("/login", { waitUntil: "networkidle" });
      await expect(page.getByText(`Signed in as ${E2E_STAFF_EMAIL}`)).toBeVisible();

      await page.getByRole("button", { name: "Sign out" }).click();

      await page.goto("/chart-of-accounts", { waitUntil: "networkidle" });
      await expect(page.getByText("You need to sign in to view the chart of accounts.")).toBeVisible();
    });
  });
});

const PAYROLL_EMPLOYEE_NAME = "E2E Payroll Employee";

test.describe("ERP payroll lifecycle", () => {
  test("login, create a salaried employee, draft payroll, post it, and the real journal entry balances", async ({ page }) => {
    page.on("dialog", (dialog) => dialog.accept());

    await test.step("login", async () => {
      await page.goto("/login", { waitUntil: "networkidle" });
      await page.locator("#email").fill(E2E_STAFF_EMAIL);
      await page.locator("#password").fill(E2E_STAFF_PASSWORD);
      await page.getByRole("button", { name: "Sign in" }).click();
      await expect(page.getByText(`Signed in as ${E2E_STAFF_EMAIL}`)).toBeVisible();
    });

    await test.step("create a real salaried employee", async () => {
      await page.goto("/employees", { waitUntil: "networkidle" });
      await page.locator("#fullName").fill(PAYROLL_EMPLOYEE_NAME);
      await page.locator("#position").fill("Dive Instructor");
      await page.locator("#hireDate").fill("2026-01-01");
      await page.locator("#amount").fill("15000.00");
      await page.locator("#currencyCode").fill("EGP");
      await page.getByRole("button", { name: "Create employee" }).click();
      await expect(page.getByText(PAYROLL_EMPLOYEE_NAME)).toBeVisible();
    });

    let runRow = page.locator("li", { hasText: "2026-08-01 – 2026-08-31" });

    await test.step("create a draft payroll run", async () => {
      await page.goto("/payroll", { waitUntil: "networkidle" });
      await page.locator("#payPeriodStart").fill("2026-08-01");
      await page.locator("#payPeriodEnd").fill("2026-08-31");
      await page.getByRole("button", { name: "Create draft" }).click();

      await expect(runRow).toBeVisible();
      await expect(runRow.getByText("DRAFT")).toBeVisible();
      await expect(runRow.getByText(PAYROLL_EMPLOYEE_NAME)).toBeVisible();
    });

    await test.step("post the draft, creating a real journal entry", async () => {
      await runRow.getByRole("button", { name: "Post" }).click();
      await page.reload();
      runRow = page.locator("li", { hasText: "2026-08-01 – 2026-08-31" });
      await expect(runRow.getByText("POSTED")).toBeVisible();
    });

    await test.step("the real journal entry it created actually balances", async () => {
      await page.goto("/journal-entries", { waitUntil: "networkidle" });
      const entryRow = page.locator("li", { hasText: "Payroll for 2026-08-01 to 2026-08-31" });
      await expect(entryRow).toBeVisible();
      await expect(entryRow.getByText("15000.00 EGP debit / 15000.00 EGP credit")).toBeVisible();
      await expect(entryRow.getByText("DEBIT 15000.00")).toBeVisible();
      await expect(entryRow.getByText("CREDIT 15000.00")).toBeVisible();
    });

    await test.step("a posted run can no longer be posted or discarded", async () => {
      await page.goto("/payroll", { waitUntil: "networkidle" });
      runRow = page.locator("li", { hasText: "2026-08-01 – 2026-08-31" });
      // Post/Discard are only rendered for DRAFT runs — the real proof
      // this run is permanent is that neither button exists anymore.
      await expect(runRow.getByRole("button", { name: "Post" })).toHaveCount(0);
      await expect(runRow.getByRole("button", { name: "Discard" })).toHaveCount(0);
    });

    await test.step("logout ends the session", async () => {
      await page.goto("/login", { waitUntil: "networkidle" });
      await expect(page.getByText(`Signed in as ${E2E_STAFF_EMAIL}`)).toBeVisible();

      await page.getByRole("button", { name: "Sign out" }).click();

      await page.goto("/payroll", { waitUntil: "networkidle" });
      await expect(page.getByText("You need to sign in to view payroll runs.")).toBeVisible();
    });
  });
});

// Distinct from every other account code this suite creates elsewhere
// (e.g. the accounting-lifecycle test's own 9910/9920) — account codes
// are unique, and this suite runs all its describe blocks against one
// shared database in one run.
const REPORT_CASH_CODE = "9930";
const REPORT_REVENUE_CODE = "9940";

test.describe("ERP financial reports lifecycle", () => {
  test("login, post a real entry, and all three reports reflect it while the fundamental invariants hold", async ({ page }) => {
    page.on("dialog", (dialog) => dialog.accept());

    await test.step("login", async () => {
      await page.goto("/login", { waitUntil: "networkidle" });
      await page.locator("#email").fill(E2E_STAFF_EMAIL);
      await page.locator("#password").fill(E2E_STAFF_PASSWORD);
      await page.getByRole("button", { name: "Sign in" }).click();
      await expect(page.getByText(`Signed in as ${E2E_STAFF_EMAIL}`)).toBeVisible();
    });

    await test.step("create two real accounts and post a real balanced entry between them", async () => {
      await page.goto("/chart-of-accounts", { waitUntil: "networkidle" });
      await page.locator("#code").fill(REPORT_CASH_CODE);
      await page.locator("#name").fill("E2E Report Cash");
      await page.locator("#accountType").selectOption("ASSET");
      await page.getByRole("button", { name: "Create account" }).click();
      await expect(page.getByText(`${REPORT_CASH_CODE} · E2E Report Cash`)).toBeVisible();

      await page.locator("#code").fill(REPORT_REVENUE_CODE);
      await page.locator("#name").fill("E2E Report Revenue");
      await page.locator("#accountType").selectOption("REVENUE");
      await page.getByRole("button", { name: "Create account" }).click();
      await expect(page.getByText(`${REPORT_REVENUE_CODE} · E2E Report Revenue`)).toBeVisible();

      await page.goto("/journal-entries", { waitUntil: "networkidle" });
      await page.locator("#entryDate").fill("2026-08-20");
      await page.locator("#description").fill("E2E report test posting");
      await page.locator("#line-account-0").selectOption({ label: `${REPORT_CASH_CODE} · E2E Report Cash` });
      await page.locator("#line-amount-0").fill("321.00");
      await page.locator("#line-account-1").selectOption({ label: `${REPORT_REVENUE_CODE} · E2E Report Revenue` });
      await page.locator("#line-direction-1").selectOption("CREDIT");
      await page.locator("#line-amount-1").fill("321.00");
      await page.getByRole("button", { name: "Post entry" }).click();
      await expect(page.getByText("E2E report test posting")).toBeVisible();
    });

    await test.step("the trial balance shows the real posted amounts and genuinely balances", async () => {
      await page.goto("/reports", { waitUntil: "networkidle" });
      await page.locator("#trialBalanceDate").fill("2026-08-31");
      const runButtons = page.getByRole("button", { name: "Run" });
      await runButtons.nth(0).click();

      await expect(page.getByText(`${REPORT_CASH_CODE} · E2E Report Cash`)).toBeVisible();
      await expect(page.getByText(`${REPORT_REVENUE_CODE} · E2E Report Revenue`)).toBeVisible();
      const totalsText = page.getByText(/Total debits [\d,.]+ · Total credits [\d,.]+/);
      await expect(totalsText).toBeVisible();
      const totals = await totalsText.textContent();
      const [, debits, credits] = totals?.match(/Total debits ([\d.]+) · Total credits ([\d.]+)/) ?? [];
      expect(debits).toBe(credits);
    });

    await test.step("the income statement shows the real revenue line", async () => {
      await page.locator("#incomeFrom").fill("2026-08-01");
      await page.locator("#incomeTo").fill("2026-08-31");
      const runButtons = page.getByRole("button", { name: "Run" });
      await runButtons.nth(1).click();

      await expect(page.getByText(`${REPORT_REVENUE_CODE} · E2E Report Revenue`)).toBeVisible();
      // Exact match: the trial balance section above still renders its own
      // totals line (e.g. "Total debits 15321.00 · ..."), which contains
      // "321.00" as a loose substring.
      await expect(page.getByText("321.00", { exact: true })).toBeVisible();
    });

    await test.step("the balance sheet includes the synthesized retained earnings line and genuinely balances", async () => {
      await page.locator("#balanceSheetDate").fill("2026-08-31");
      const runButtons = page.getByRole("button", { name: "Run" });
      await runButtons.nth(2).click();

      await expect(page.getByText(`${REPORT_CASH_CODE} · E2E Report Cash`)).toBeVisible();
      await expect(page.getByText("Retained Earnings (accumulated)")).toBeVisible();
      // Other lifecycle tests in this shared suite post real entries against
      // the same ledger (e.g. payroll posts a real salaries expense), so
      // equity here can genuinely be negative — the regexes must allow a
      // leading "-", not just digits.
      const assetsText = await page.getByText(/Total assets -?[\d,.]+/).textContent();
      const equityText = await page.getByText(/Total equity -?[\d,.]+/).textContent();
      const liabilitiesText = await page.getByText(/Total liabilities -?[\d,.]+/).textContent();
      const totalAssets = Number(assetsText?.match(/Total assets (-?[\d.]+)/)?.[1]);
      const totalEquity = Number(equityText?.match(/Total equity (-?[\d.]+)/)?.[1]);
      const totalLiabilities = Number(liabilitiesText?.match(/Total liabilities (-?[\d.]+)/)?.[1]);
      expect(totalAssets).toBeCloseTo(totalLiabilities + totalEquity, 2);
    });

    await test.step("logout ends the session", async () => {
      await page.goto("/login", { waitUntil: "networkidle" });
      await expect(page.getByText(`Signed in as ${E2E_STAFF_EMAIL}`)).toBeVisible();

      await page.getByRole("button", { name: "Sign out" }).click();

      await page.goto("/reports", { waitUntil: "networkidle" });
      await expect(page.getByText("You need to sign in to view financial reports.")).toBeVisible();
    });
  });
});
