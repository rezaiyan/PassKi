import type { Metadata } from "next";
import { GeneratorForm } from "./GeneratorForm";

export const metadata: Metadata = { title: "Generator" };

export default function GeneratorPage() {
  return (
    <>
      <h1>Generator</h1>
      <p className="lead">Everything is computed from public data: package names, certificate fingerprints, Team and bundle IDs. Keystores never leave your machine.</p>
      <GeneratorForm />
    </>
  );
}
