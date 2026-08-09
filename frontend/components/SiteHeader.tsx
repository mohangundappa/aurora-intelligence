import Link from "next/link";

export function SiteHeader({
  surface = "site",
}: {
  surface?: "site" | "console";
}) {
  return (
    <header
      className={`site-header ${surface === "console" ? "site-header-console" : ""}`}
    >
      <Link className="brand" href="/">
        AURORA HOTELS
      </Link>
      <nav aria-label="Primary navigation">
        <Link href="/login">Account</Link>
        <Link href="/console">Intelligence console</Link>
      </nav>
    </header>
  );
}
