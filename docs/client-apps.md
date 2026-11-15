# Client apps and approval

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
