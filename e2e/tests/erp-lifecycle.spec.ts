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
