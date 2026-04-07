# SeersCMP Android SDK

Seers Consent Management Platform SDK for Android.

## Installation

### Gradle (via JitPack)

**Step 1** — Add JitPack to your root `settings.gradle`:
```gradle
dependencyResolutionManagement {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

**Step 2** — Add dependency to your app `build.gradle`:
```gradle
dependencies {
    implementation 'com.github.NickSpencer511:seers-cmp-android:1.0.1'
}
```

## Usage

```kotlin
import ai.seers.cmp.SeersCMP

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SeersCMP.initialize(this, settingsId = "YOUR_SETTINGS_ID")
    }
}
```

Get your **Settings ID** from [seers.ai](https://seers.ai) dashboard → Mobile Apps → Get Code.

## What it does automatically
- ✅ Shows consent banner based on your dashboard settings
- ✅ Detects user region (GDPR / CPRA / none)
- ✅ Blocks trackers until consent is given
- ✅ Saves consent to device (SharedPreferences)
- ✅ Logs consent to your Seers dashboard
