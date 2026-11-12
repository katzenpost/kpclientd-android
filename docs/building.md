# Building kpclientd-android

The app has two parts:

* the `kpclientd` daemon, which connects to the Katzenpost mixnet and serves
client apps. It ships in the APK as `libkpclientd.so` and runs as a process of
the app, listening only on a unix socket in the app's private directory.

* the GUI, where the daemon is started and stopped, and where the user decides
which apps may use it.

Every step is a make target, and CI calls the same targets. You need JDK 21,
the Android SDK's command-line tools (`ANDROID_HOME`, by default
`~/Android/Sdk`), git, and any Go of 1.21 or later, which fetches the exact Go
pinned in `dependencies.txt`.

| Target | |
|---|---|
| `make sdk` | the pinned NDK, platform and build tools |
| `make daemon` | clones Katzenpost at the version in `dependencies.txt` into `build/katzenpost` (or uses `KATZENPOST_REPO`) and builds `kpclientd` for both ABIs |
| `make apk` | the daemon, then the debug and unsigned release APKs, in `apk/app/build/outputs/apk` |
| `make test` | the unit tests |
| `make test-approval APK=…` | on the device or emulator adb reaches, checks that no app reaches the daemon without the user's answer |
| `make release` | the release APKs into `dist/`, unsigned, and signed beside them when `KEYSTORE` (a `.jks`) and `KEYSTORE_PASSWORD` are set; with `SHA256SUMS` |
| `make repro` | builds the arm64 daemon and the unsigned APK twice each from clean copies, and checks both are the same |

The Gradle wrapper fetches Gradle 9.5.0 and checks it against the checksum in
`apk/gradle/wrapper/gradle-wrapper.properties`; `make` checks the wrapper jar
itself, and Gradle checks every dependency and plugin it fetches against
`apk/gradle/verification-metadata.xml`. After changing a dependency, refresh
that file from an empty cache, since a warm one leaves out what it already
holds, and review the diff:

```bash
cd apk && GRADLE_USER_HOME=$(mktemp -d) ./gradlew --write-verification-metadata sha256 \
    assembleDebug assembleRelease testDebugUnitTest assembleDebugAndroidTest
```

## Releases

A tag `v*` runs CI, then builds the daemon and the APKs on two runners, in
different directories, and publishes them as a GitHub release only if both
builds are byte for byte the same. The tag must name the app's `versionName`.
The build is reproducible: Go, Katzenpost, the NDK, the build tools, Gradle
and the Android Gradle plugin are pinned, and paths, build ids and dates are
kept out of the output.

To check a release, build it yourself and compare the unsigned APK with the
release's:

```bash
git checkout v1.2.0
make sdk && VERSION=v1.2.0 make release
sha256sum dist/*-unsigned.apk
```

The signed APK differs from the unsigned one only by its signature.
