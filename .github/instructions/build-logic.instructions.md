---
applyTo: "build-logic/**/*.kt"
---

# Build-Logic Convention Plugin Rules

- Prefer lazy Gradle configuration (`configureEach`, `withPlugin`, provider APIs).
- Avoid `afterEvaluate` unless there is no viable lazy alternative.
- Check `gradle/libs.versions.toml` for version catalog aliases before adding new ones.
- Convention plugins: `com.ntsocial.meshlink.kmp.feature`, `com.ntsocial.meshlink.kmp.library`, `com.ntsocial.meshlink.kmp.jvm.android`, `com.ntsocial.meshlink.koin`.
