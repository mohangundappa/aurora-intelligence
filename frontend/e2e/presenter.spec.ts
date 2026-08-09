import { expect, test } from "@playwright/test";

test("presenter path renders events, explanations, identity, and outcome", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByLabel("Where are you going?").fill("Miami");
  await page.getByLabel("Check in").fill("2026-06-05");
  await page.getByLabel("Check out").fill("2026-06-08");
  await page.getByLabel("Adults").selectOption("2");
  await page.getByLabel("Children").selectOption("2");
  await page.getByRole("button", { name: "Search stays" }).click();
  await expect(
    page.getByRole("heading", { name: /Stays in Miami/ }),
  ).toBeVisible();
  await page.getByLabel("Pool").check();
  await page.getByRole("link", { name: "View property" }).first().click();
  await expect(
    page.getByRole("heading", { name: "Choose a room" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "See room details" }).first().click();
  await page.getByRole("link", { name: "Select room" }).first().click();
  await expect(page.getByText("Complete your stay")).toBeVisible();
  await page
    .getByRole("button", { name: /Trigger presenter abandonment/ })
    .click();

  await page.goto("/console");
  await expect(
    page.getByRole("heading", { name: /See the why/ }),
  ).toBeVisible();
  await expect(
    page.getByText("DERIVED SIGNALS", { exact: true }),
  ).toBeVisible();
  await expect(page.locator(".signal-row").first()).toBeVisible();
  await page.goto("/console/funnel");
  await expect(page.getByText("CONVERSION FUNNEL")).toBeVisible();
  await page.goto("/login");
  await expect(
    page.getByRole("heading", { name: /Welcome back/ }),
  ).toBeVisible();
});
