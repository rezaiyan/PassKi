import type { Metadata } from "next";
import { GeneratorForm } from "./GeneratorForm";

export const metadata: Metadata = { title: "Generator" };

export default function GeneratorPage() {
  return (
    <>
      <header className="page-band">
        <div className="container page-band-inner">
          <h1>Generator</h1>
          <p className="lead">
            Every file your domain and apps need, computed from public data:
            package names, certificate fingerprints, Team and bundle IDs.
            Keystores never leave your machine. With Gradle,{" "}
            <code>./gradlew passkiSetup</code> writes the same files.
          </p>
        </div>
      </header>
      <div className="container page-body">
        <GeneratorForm />
      </div>
    </>
  );
}
