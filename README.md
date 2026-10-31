# kpclientd-android

Run [Katzenpost](https://github.com/katzenpost/katzenpost) client daemon
`kpclientd` on Android devices.

## About this copy

This repository is a copy of
[bfix/kpclientd-android](https://github.com/bfix/kpclientd-android), made
without first asking its author for permission. kpclientd-android was started
by Bernd Fix, who is not part of the Katzenpost team. We appreciate his work
and intend to build on it here. The copy also includes the reproducible-build
changes from gabrix73 in
[bfix/kpclientd-android#1](https://github.com/bfix/kpclientd-android/pull/1).

We would gladly hand this responsibility over to someone else. For the time
being, what is published here is code that works on our own Android phones. We
hope this work will one day be useful to people who want to use Katzenpost,
not only to developers working on it.

## License

Copyright (C) 2026-present, Bernd Fix   >Y<

'kpclientd-android' is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License,
or (at your option) any later version.

'kpclientd-android' is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http:www.gnu.org/licenses/>.

SPDX-License-Identifier: AGPL3.0-or-later

## Caveat

This is early work. It runs on our own phones and on the emulator in CI, but
expect rough edges.

## Installing

Each [release](https://github.com/katzenpost/kpclientd-android/releases) has
an APK for each ABI: `arm64-v8a` for phones, `x86_64` for emulators. Install
the signed one, `kpclientd-android-<version>-<abi>.apk`. Releases are all
signed with the same key, so each installs over the last.

Android 13 or later is needed; no root is.

## Building

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

### Releases

A tag `v*` runs CI, then builds the daemon and the APKs on two runners, in
different directories, and publishes them as a GitHub release only if both
builds are byte for byte the same. The tag must name the app's `versionName`.
The build is reproducible: Go, Katzenpost, the NDK, the build tools, Gradle
and the Android Gradle plugin are pinned, and paths, build ids and dates are
kept out of the output.

To check a release, build it yourself and compare the unsigned APK with the
release's:

```bash
git checkout v1.1.0
make sdk && VERSION=v1.1.0 make release
sha256sum dist/*-unsigned.apk
```

The signed APK differs from the unsigned one only by its signature.

## Running the app

The daemon requires a configuration file (usually called `client.toml`) to
successfully connect to the Katzenpost mixnet. A config file is built-in,
but it may be outdated the time you want to run the daemon.

Because this file is currently not public (it resides in a private repository
`github.com/katzenpost/namenlos`) you need to contact the Katzenpost project
and ask for a new version. Copy the new `client.toml` to your device and pick
it with **Import config**; **Use built-in** goes back to the built-in one.
Whichever is used, the app replaces its `[Listen]` section, so that the daemon
listens only on its private unix socket.

Click on the app icon to open the GUI. Below the title line you see a line like
`Configuration: ...` where either `built-in` or `custom` is specified.

## Using the app

If the app is started it will display the first of the following screens:

| | | |
| :---: | :---: | :---: |
| <img alt="kpclientd screenshot 1" src="images/1.png"> | <img alt="kpclientd screenshot 2" src="images/2.png"> | <img alt="kpclientd screenshot 3" src="images/3.png"> |

There are two buttons to start and stop the daemon and the elapsed time in
either state is displayed below (format "&lt;hrs&gt;:&lt;mins&gt;").

You can toggle the log view with the chevron button above the window.

## Which apps may use the daemon

No app reaches the daemon unless you allow it. The first time an app asks, a
notification asks you, and the app can open the same question itself. It shows
the app's icon, name and package, with **Don't allow** and **Allow**. Allow
only takes a tap a second after the question opens, and the question hides
other apps' overlays while it is shown.

`make test-approval APK=…` checks all of this on a device or emulator over
adb; CI runs it on an emulator.

Your answer is kept for that package signed with that certificate. The same
package signed with another key is a different app, and is asked about again.
The **Apps** list on the main screen shows every app you answered about.
**Forget** takes the answer back and restarts the daemon, which ends every
connection, so an app you forgot is cut off at once.

## For client apps

An app asks for a connection through a content provider:

```java
Bundle r = getContentResolver().call(
        Uri.parse("content://org.hoi_polloi.kpdaemon.connect"), "connect", null, null);
```

The app must declare, in its manifest:

```xml
<queries>
    <provider android:authorities="org.hoi_polloi.kpdaemon.connect" />
</queries>
```

The Bundle holds either:

* `socket`: a `ParcelFileDescriptor` of a unix socket connected to kpclientd.
  Speak the thin client protocol over it, as over the daemon's socket; or
* `error`, which is one of:
  * `approval`: you haven't answered about this app yet. `approve` is then a
    `PendingIntent` that opens the question.
  * `denied`: you refused.
  * `stopped`: the daemon isn't running.
  * `unknown`: the caller can't be identified.

kpclientd-android knows the caller by its Binder identity, which the kernel
provides, never by anything the caller says. A client app needs no internet
permission, and no other app can reach the daemon without your answer.
