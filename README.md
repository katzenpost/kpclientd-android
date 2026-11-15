# kpclientd-android

An Android app that runs the [Katzenpost](https://github.com/katzenpost)
client daemon `kpclientd` on the phone and decides which other apps may use
it. It is for people who want Katzenpost client apps on Android, and for the
developers of those apps.

The app starts and stops the daemon and holds its configuration. It is not a
messenger: client apps such as chat programs talk to the daemon through it.
On a desktop, run `kpclientd` from the Katzenpost repository instead.

| | | |
| :---: | :---: | :---: |
| <img alt="kpclientd screenshot 1" src="images/1.png"> | <img alt="kpclientd screenshot 2" src="images/2.png"> | <img alt="kpclientd screenshot 3" src="images/3.png"> |

## Install and run

Each [release](https://github.com/katzenpost/kpclientd-android/releases) has
an APK per ABI: `arm64-v8a` for phones, `x86_64` for emulators. Install the
signed one, `kpclientd-android-<version>-<abi>.apk`; every release is signed
with the same key, so each installs over the last. Android 13 or later is
needed; root is not.

Open **KP Daemon** and start the daemon; the main screen shows how long it
has been running or stopped, whether the configuration is `built-in` or
`custom`, and, behind the chevron, the daemon's log. The daemon needs a `client.toml` for the
mixnet. One is built in, but it may be out of date; the current one is not
public, so ask the Katzenpost project for it, copy it to the phone and pick
it with **Import config**. **Use built-in** goes back to the built-in one.
Either way the app replaces the `[Listen]` section, so the daemon listens
only on a unix socket in the app's private directory.

No app reaches the daemon until you allow it. How a client app asks for a
connection, and how approval works, is in
[docs/client-apps.md](docs/client-apps.md).

## Build from source

You need JDK 21, the Android SDK command-line tools in `ANDROID_HOME`
(default `~/Android/Sdk`), git, and Go 1.21 or later.

```bash
make sdk apk   # the pinned NDK and SDK, the daemon for both ABIs, the APKs
make test      # the unit tests
```

The APKs land in `apk/app/build/outputs/apk`. Every make target, the
reproducible release build and how to check a release are in
[docs/building.md](docs/building.md).

## What is here

* `apk/`: the Android app (Gradle project), Java sources in
  `apk/app/src/main/java/org/hoi_polloi/kpdaemon`.
* `mk_daemon`, `mk_apk`, `mk_release`: build the daemon, the APKs and a release.
* `verify_daemon_reproducible`, `verify_apk_reproducible`: build twice and compare.
* `test_approval`: on a device or emulator, checks that no app reaches the
  daemon without the user's answer.
* `dependencies.txt`: the pinned Go and Katzenpost versions.

## Status

Version 1.2.0. This is early work: it runs on our own phones and on the
emulator in CI, but expect rough edges. The daemon is built from Katzenpost
at the commit pinned in `dependencies.txt`; client apps speak that version's
thin client protocol over the socket the app hands them.

## About this copy

This repository is a copy of
[bfix/kpclientd-android](https://github.com/bfix/kpclientd-android), made
without first asking its author for permission. kpclientd-android was started
by Bernd Fix, who is not part of the Katzenpost team; we build on his work
here. The copy also includes the reproducible-build changes from gabrix73 in
[bfix/kpclientd-android#1](https://github.com/bfix/kpclientd-android/pull/1).
We would gladly hand this responsibility over to someone else.

## License

Copyright (C) 2026-present, Bernd Fix >Y<

Free software under the GNU Affero General Public License, version 3 or (at
your option) any later version; see [LICENSE](LICENSE).
SPDX-License-Identifier: AGPL-3.0-or-later
