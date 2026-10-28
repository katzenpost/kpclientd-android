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

THIS IS WORK-IN-PROGRESS AT A VERY EARLY STATE. DON'T EXPECT ANY COMPLETE
DOCUMENTATION OR COMPILABLE, RUNNABLE OR EVEN OPERATIONAL SOURCE CODE.

## Prerequisites

* arm64-based device with Android 13+ (recommended: Android 16), or an
x86_64 emulator for testing; no root is needed
* a cloned Katzenpost repo
* the exact Go version listed in `dependencies.txt`
* Java 17+
* Android SDK and NDK installed

## Building the app

The app contains two separate parts:

* the `kpclientd` daemon that connects to the Katzenpost mixnet and serves
client apps. It ships in the APK as `libkpclientd.so` and runs as a process of
the app, listening only on a unix socket in the app's private directory.

* the GUI where the daemon can be started and stopped, and where the user
decides which apps may use it.

### Compiling kpclientd

The build uses the Katzenpost and Go versions pinned in `dependencies.txt`.
Set `KATZENPOST_REPO` and `ANDROID_NDK_ROOT` when they are not installed in
the default locations:

To build the program, run:

```bash
KATZENPOST_REPO=/path/to/katzenpost \
ANDROID_NDK_ROOT=/path/to/android-ndk \
./mk_daemon
```

It writes `apk/app/src/main/jniLibs/arm64-v8a/libkpclientd.so`; `./mk_daemon amd64`
writes the x86_64 one, for the emulator. The command displays the resulting
file properties at the end of the build.
With the currently pinned toolchain, the stripped file is around 20MB.

To verify that two clean arm64 builds produce the same daemon binary, run:

```bash
KATZENPOST_REPO=/path/to/katzenpost \
ANDROID_NDK_ROOT=/path/to/android-ndk \
./verify_daemon_reproducible
```

### Building the GUI app

#### Gradle wrapper

The repository includes the Gradle wrapper. It downloads Gradle 9.5.0 and
verifies the distribution with the checksum pinned in
`apk/gradle/wrapper/gradle-wrapper.properties`; a system Gradle installation
is not required.

#### Preparing for build

Signing is optional and deliberately separate from the reproducible unsigned
artifact. Set the following variables only when a signed local APK is needed:

```toml
ANDROID_BUILD_TOOLS=/path/to/android-sdk/build-tools/36.0.0
KEYSTORE=/path/to/keystore.jks
KEY_ALIAS=dev
```

If you have no keystore yet (to store keys required to sign APKs), create one
in a folder of your choice and set the location in the file above:

```bash
keytool -genkey -v \
  -keystore <folder>/keystore.jks -alias dev \
  -keyalg RSA -keysize 2048 -validity 10000
```

You also need to create an output folder:

```bash
mkdir ./apk/deploy
```

#### Building and installing the APK

To build the APK, run the following commands:

```bash
./mk_apk
```

Without `KEYSTORE`, the build preserves
`apk/deploy/kpclientd-release-unsigned.apk` for reproducibility checks.
With `KEYSTORE`, it additionally produces a locally signed APK.

After building `kpclientd`, verify two clean unsigned APK builds with:

```bash
ANDROID_SDK_ROOT=/path/to/android-sdk \
JAVA_HOME=/path/to/jdk-17 \
./verify_apk_reproducible
```

You can add the `+deploy` argument if you want the build script to upload
the resulting binaries (`kpclientd.apk` and `kpclientd-debug.apk`) to the
`Download` folder on a device:

```bash
KEYSTORE=/path/to/keystore.jks KEY_ALIAS=dev ./mk_apk +deploy
```

### Running the app

Install the `kpclientd.apk` on your device, but don't open it yet.

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

### Using the app

If the app is started it will display the first of the following screens:

| | | |
| :---: | :---: | :---: |
| <img alt="kpclientd screenshot 1" src="images/1.png"> | <img alt="kpclientd screenshot 2" src="images/2.png"> | <img alt="kpclientd screenshot 3" src="images/3.png"> |

There are two buttons to start and stop the daemon and the elapsed time in
either state is displayed below (format "&lt;hrs&gt;:&lt;mins&gt;").

You can toggle the log view with the chevron button above the window.

### Which apps may use the daemon

No app reaches the daemon unless you allow it. The first time an app asks, a
notification asks you, and the app can open the same question itself. It shows
the app's icon, name and package, with **Don't allow** and **Allow**. Allow
only takes a tap a second after the question opens, and the question hides
other apps' overlays while it is shown.

`./test_approval APK` checks all of this on a device or emulator over adb; CI
runs it on an emulator. A tag `v*` publishes a signed APK, after building the
daemon and the APK twice each and checking that both builds are the same.

Your answer is kept for that package signed with that certificate. The same
package signed with another key is a different app, and is asked about again.
The **Apps** list on the main screen shows every app you answered about.
**Forget** takes the answer back and restarts the daemon, which ends every
connection, so an app you forgot is cut off at once.

### For client apps

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
