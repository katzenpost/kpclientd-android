# SPDX-License-Identifier: AGPL-3.0-only

ANDROID_HOME ?= $(HOME)/Android/Sdk
NDK ?= 28.0.13004108
KATZENPOST := $(shell sed -n 's/^katzenpost=//p' dependencies.txt)
KATZENPOST_REPO ?= $(CURDIR)/build/katzenpost
VERSION ?= $(shell git describe --tags --always)
# From https://services.gradle.org/distributions/gradle-9.5.0-wrapper.jar.sha256
GRADLE_WRAPPER_SHA256 := 497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7

export ANDROID_HOME
export ANDROID_SDK_ROOT := $(ANDROID_HOME)
export ANDROID_NDK_ROOT := $(ANDROID_HOME)/ndk/$(NDK)
export KATZENPOST_REPO VERSION

.PHONY: sdk daemon apk wrapper test release repro test-approval clean

# The pinned NDK, platform and build tools.
sdk:
	yes | $(ANDROID_HOME)/cmdline-tools/latest/bin/sdkmanager \
		"ndk;$(NDK)" "platforms;android-36" "build-tools;36.0.0" >/dev/null

# Katzenpost at the version in dependencies.txt.
$(KATZENPOST_REPO):
	git clone --quiet --branch $(KATZENPOST) https://github.com/katzenpost/katzenpost $@

# kpclientd for both ABIs, into the app's native libraries.
daemon: $(KATZENPOST_REPO)
	./mk_daemon arm64
	./mk_daemon amd64

# The debug and unsigned release APKs, in apk/app/build/outputs/apk.
apk: daemon wrapper
	./mk_apk

# The Gradle wrapper jar is the one services.gradle.org publishes for gradle-wrapper.properties'
# version; Gradle itself checks the distribution it fetches and, with verification-metadata.xml,
# every dependency.
wrapper:
	echo "$(GRADLE_WRAPPER_SHA256)  apk/gradle/wrapper/gradle-wrapper.jar" | sha256sum -c --quiet

test: wrapper
	cd apk && ./gradlew --no-daemon testDebugUnitTest

# The release APKs into dist/, signed with $KEYSTORE if it is set.
release: daemon wrapper
	./mk_release

# Two clean builds each of the arm64 daemon and the unsigned APK are the same.
repro: daemon
	./verify_daemon_reproducible
	./verify_apk_reproducible

# On the device or emulator adb reaches: no app reaches the daemon without the user's answer.
test-approval:
	./test_approval $(APK)

clean:
	rm -rf dist apk/deploy apk/app/build apk/app/src/main/jniLibs
