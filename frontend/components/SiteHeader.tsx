import Link from "next/link";

export function SiteHeader() {
  return (
    <header className="site-header">
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
