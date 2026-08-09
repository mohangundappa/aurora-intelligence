import Link from "next/link";
import { SiteHeader } from "../../components/SiteHeader";

export default function Confirmation() {
  return (
    <main>
      <div className="shell">
        <SiteHeader />
        <section className="confirmation">
          <div className="eyebrow">Aurora Hotels</div>
          <h1>A little more room for what matters.</h1>
          <p className="lede">
            Your fictional reservation is safely in the demo record. Thank you
            for exploring with us.
          </p>
          <Link className="button" href="/">
            Plan another stay
          </Link>
        </section>
      </div>
    </main>
  );
}
