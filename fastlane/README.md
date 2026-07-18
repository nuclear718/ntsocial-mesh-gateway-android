fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android internal

```sh
[bundle exec] fastlane android internal
```

Builds the `googleRelease` AAB and publishes it to Google Play's internal track. The variant name is
kept for Play workflow compatibility; the app runtime does not require Google Cloud, Maps, Firebase,
Crashlytics, Datadog, or ML Kit credentials.

### android fdroid_build

```sh
[bundle exec] fastlane android fdroid_build
```

Builds the F-Droid release APK.

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
