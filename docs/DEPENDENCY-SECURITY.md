# Dependency security maintenance

The November 2026 review identified known advisories in transitive annotation-processor and Android/Kotlin build/test tooling dependencies. These were outside the resolved Android release runtime graph. They have been upgraded in the compatible release families below, rather than suppressing scanner findings or removing tooling from the scan.

| Family | Selected version | Scope / reason |
| --- | --- | --- |
| Guava | 33.7.2-jre | Room compiler; deserialization advisory fix |
| Protobuf java, kotlin, java-util | 3.25.5 | Android unified-test-platform dependencies; align the 3.x family |
| Netty 4.1 modules | 4.1.137.Final | Unified-test-platform/gRPC transport; align coupled modules without switching to 4.2 |
| OpenTelemetry API/context | 1.67.0 | Kotlin tooling's Swift-export classpath |
| Bouncy Castle jdk18on modules | 1.85 | Kotlin tooling's signing/crypto classpath; align provider, PKIX, PGP and util |

These are targeted resolution rules in `app/build.gradle.kts`, applied only to already-requested dependencies. They do not add new application runtime dependencies. The 343-coordinate lockfile and SHA-256 artifact verification metadata capture the actual resolved versions, including internal tooling configurations. Do not hand-edit only a lockfile line or assume a successful app build exercises the test runner.

Recheck known advisories with `python3 scripts/audit-dependencies.py`. It queries OSV for all exact locked Maven coordinates, returns failure on advisory matches or incomplete/network responses, and writes its dated report under ignored artifacts. CI runs the same check. Zero listed matches is not a claim of zero unknown vulnerabilities or an assessment of every possible execution path.

Remediation verification completed: 38 JVM tests, debug/release lint, debug/release APK builds, and 50 connected emulator tests through the actual Gradle unified test platform all passed. The final online OSV check returned zero known listed matches across all 343 locked coordinates.

After a change run unit tests, debug/release lint and builds, plus connected emulator tests through Gradle's unified test platform. Keep synthetic fixture suites on disposable emulators. Review release runtime coordinates separately when a rule could affect application code. Android's SDK/platform and proprietary dependencies still need normal vendor maintenance.

References: [OSV API](https://google.github.io/osv.dev/api/), [Gradle dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html), [Guava advisory](https://github.com/advisories/GHSA-xxph-c9ww-hj94). Exact advisory IDs and the historical pre-fix response are retained in private audit artifacts; the previous affected-versions table in OPEN-SOURCE-REVIEW.md is historical, not a current unresolved finding.
