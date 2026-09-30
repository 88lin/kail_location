# Kail Location (Local Build)

> A spatial control deck for developers: route simulation, location simulation, navigation simulation, step-frequency simulation, virtual location and NFC simulation.
>
> This is a personal local build: login / subscription / usage limits, ads and telemetry removed. All features unlocked without sign-in. For the owner's own debugging use only, not for distribution.

## Build

```bash
./gradlew assembleDebug
```

CI: pushing to `local-free` triggers a GitHub Actions build (artifacts + releases).

## Baidu Map Key (required for map & search)

Source builds ship without a Baidu key:

1. Create an app at https://lbsyun.baidu.com/apiconsole/key (type: Android app)
2. Package name `com.kail.location` + this build's signing SHA1
3. Paste the AK in Settings -> Baidu Map Key, then restart the app

## Usage

For development and professional testing in lawful, authorized environments only.

Note: ROOT mode requires the device to be up for at least 100 seconds before starting a simulation.

## License

GPL-3.0
