.PHONY: setup android webclient deploy-web cloud-typecheck harness latency release

ANDROID_APK := android/app/build/outputs/apk/debug/app-debug.apk
VERSION_CODE ?=
VERSION_NAME ?=

setup:
	@echo "== webclient =="
	cd webclient && npm install
	@echo "== cloud =="
	cd cloud && npm install
	@echo "== validate/latency-harness (python) =="
	cd validate/latency-harness && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt || true
	@echo "== android SDK check =="
	@test -n "$$ANDROID_HOME" && echo "ANDROID_HOME=$$ANDROID_HOME" || echo "ANDROID_HOME not set - required for the android target"

android:
	cd android && ./gradlew assembleDebug $(if $(VERSION_CODE),-PvehplayerVersionCode=$(VERSION_CODE)) $(if $(VERSION_NAME),-PvehplayerVersionName=$(VERSION_NAME))

webclient:
	cd webclient && npm run typecheck && npm run build

deploy-web: webclient
	cd webclient && npx wrangler deploy

cloud-typecheck:
	cd cloud && npm run typecheck

harness:
	cd webclient && npm run harness

latency:
	cd validate/latency-harness && python3 measure_latency.py --selftest

# The release lesson (docs/NEXT_SESSION.md, a real incident): builds 5/6
# shipped versionCode=1 under a build-5/6 tag because an unversioned
# `assembleDebug` run "just to be sure" got copied and published instead
# of the versioned one. This target makes the aapt verification step
# impossible to skip, not just a step someone has to remember.
#
# Usage: make release VERSION_CODE=12 VERSION_NAME=0.1.0-something-<sha> TAG=build-12
release:
	@test -n "$(VERSION_CODE)" || (echo "VERSION_CODE is required, e.g. make release VERSION_CODE=12 VERSION_NAME=... TAG=build-12" && exit 1)
	@test -n "$(VERSION_NAME)" || (echo "VERSION_NAME is required" && exit 1)
	@test -n "$(TAG)" || (echo "TAG is required, e.g. TAG=build-12" && exit 1)
	$(MAKE) android VERSION_CODE=$(VERSION_CODE) VERSION_NAME=$(VERSION_NAME)
	@echo "== verifying versionCode in the built APK matches what was requested =="
	@aapt dump badging $(ANDROID_APK) | head -1
	@aapt dump badging $(ANDROID_APK) | head -1 | grep -q "versionCode='$(VERSION_CODE)'" || (echo "versionCode mismatch - refusing to publish, see docs/NEXT_SESSION.md's release incident" && exit 1)
	cp $(ANDROID_APK) /tmp/vehplayer-debug.apk
	gh release create $(TAG) /tmp/vehplayer-debug.apk#vehplayer-debug.apk --title "$(VERSION_NAME) ($(TAG))"
	@echo "== verifying the uploaded asset is actually named vehplayer-debug.apk (UpdateChecker requires the exact name) =="
	gh release view $(TAG) --json assets --jq '.assets[].name' | grep -qx "vehplayer-debug.apk" || (echo "asset name mismatch - UpdateChecker.kt expects exactly vehplayer-debug.apk" && exit 1)
