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
  await expect(
    page.getByRole("heading", { name: /DESTINATION_DISCOVERY|MIAMI_GETAWAY/ }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Explore this recommendation" })
    .click();
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

  await page.goto("/login");
  await page.getByRole("button", { name: "Sign in to demo account" }).click();
  await expect(
    page.getByText(/now identified as an Aurora Circle member/),
  ).toBeVisible();
  const session = await page.evaluate(() =>
    sessionStorage.getItem("aurora.session"),
  );
  const anonymous = await page.evaluate(() =>
    localStorage.getItem("aurora.anonymous"),
  );
  expect(session).toBeTruthy();
  expect(anonymous).toBeTruthy();
  await expect
    .poll(async () => {
      const response = await page.request.get(
        `http://localhost:8080/api/identity/${anonymous}/timeline`,
      );
      return response.ok() ? (await response.json()).length : 0;
    })
    .toBeGreaterThan(0);

  await page.goto("/booking/aurora-miami?room=family-suite");
  await page.getByLabel("First name").fill("Demo");
  await page.getByLabel("Last name").fill("Traveler");
  await page.getByLabel("Email").fill("traveler@example.test");
  await page.getByRole("button", { name: "Confirm simulated booking" }).click();
  await expect(
    page.getByText(/simulated reservation is confirmed/),
  ).toBeVisible();

  await page.goto("/console");
  await expect(
    page.getByRole("heading", { name: /See the why/ }),
  ).toBeVisible();
  await expect(
    page.getByText("DERIVED SIGNALS", { exact: true }),
  ).toBeVisible();
  await expect(page.locator(".signal-row").first()).toBeVisible();
  await expect(page.getByText("Identity timeline")).toBeVisible();
  await page.goto("/console/funnel");
  await expect(page.getByText("CONVERSION FUNNEL")).toBeVisible();
  await expect
    .poll(async () => {
      const response = await page.request.get(
        `http://localhost:8080/api/console/sessions/${session}`,
      );
      if (!response.ok()) return false;
      const body = await response.json();
      return body.events.some(
        (event: { eventName: string }) =>
          event.eventName === "BOOKING_COMPLETED",
      );
    })
    .toBe(true);
});

test("workforce console keeps refusals and insufficient analyses explicit", async ({
  page,
}) => {
  await page.route("**/api/console/workforce", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        objectives: [
          {
            objective: {
              objectiveId: "objective-demo",
              name: "Increase direct bookings",
              description: "A seeded objective",
              status: "ACTIVE",
              targetKpi: "BOOKING_COMPLETED",
              targetValue: 10,
            },
            insights: [],
            proposals: [
              {
                proposal: {
                  proposalId: "proposal-demo",
                  experimentName: "Headline test",
                  experimentId: "experiment-demo",
                  reasoning: "Evidence-backed proposal",
                  evidenceRefs: ["tool-result-1"],
                  governanceState: "PROPOSED",
                },
                audit: [],
                activationAttempts: [],
                analyses: [
                  {
                    analysisId: "analysis-demo",
                    variants: [
                      {
                        variant: "control",
                        exposures: 2,
                        outcomes: 0,
                        conversionRate: 0,
                      },
                      {
                        variant: "treatment",
                        exposures: 1,
                        outcomes: 0,
                        conversionRate: 0,
                      },
                    ],
                    sufficientSample: false,
                    absoluteLift: null,
                    relativeLift: null,
                    recommendation: "ITERATE",
                    reasoning: "Evidence is insufficient.",
                    evidenceRefs: ["tool-result-2"],
                  },
                ],
              },
            ],
            executions: [
              {
                executionId: "execution-demo",
                agentType: "INSIGHTS",
                status: "REFUSED",
                startedAt: "2026-08-10T00:00:00Z",
                completedAt: "2026-08-10T00:00:01Z",
                latencyMilliseconds: 1000,
                output: {
                  code: "NO_SESSIONS",
                  reason: "No governed sessions matched.",
                },
                toolCalls: [],
                errors: ["NO_SESSIONS"],
              },
            ],
            timings: [],
          },
        ],
        executions: [],
        activationAttempts: [],
      }),
    });
  });

  await page.goto("/console/workforce");
  await expect(
    page.getByRole("heading", {
      name: "Follow the governed loop from objective to evidence.",
    }),
  ).toBeVisible();
  await expect(page.getByText(/Agent refusal: NO_SESSIONS/)).toBeVisible();
  await expect(
    page.getByText("Evidence guard not met. Lift and conclusion are withheld."),
  ).toBeVisible();
  await expect(page.getByText("0.0%")).not.toBeVisible();
  await expect(page.getByRole("button")).toHaveCount(0);
});
