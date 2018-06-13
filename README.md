## A3Gradle Plugin

[![Build Status](https://travis-ci.org/allaboutapps/A3GradlePlugin.svg?branch=master)](https://travis-ci.org/allaboutapps/A3GradlePlugin)
 [![Download](https://api.bintray.com/packages/allaboutapps/A3-Android/at.allaboutapps.gradle.plugin/images/download.svg) ](https://bintray.com/allaboutapps/A3-Android/at.allaboutapps.gradle.plugin/_latestVersion)


This plugin enables us (aaa) to facilitate our internal test and release workflows by having a common, shared build setup.

### Features
* **Git versioning** To support CI / CD you can leave `versionCode` and `versionName` empty. It will use the amount of commits on the current branch as a version code and `git describe` for a uniquely identifiable version name.

* **`BuildConfig.INTERNAL`** In addition to `BuildConfig.DEBUG` you can use `INTERNAL` for setup test data, add shortcuts, or include debug options. These should be features that you want during development, that should not end up in any users hands.

* **Build type `preview`** This is a pre-release build type for testing. It should have the same setup as `release`, with the only difference that debugging is enabled. This lets you use `BuildConfig.DEBUG` for logging purposes and other low-key features, while you can use `BuildConfig.INTERNAL` for more intrusive debug features.

* **Shared debug key** Projects will use a shared debug key with the path specified in your `gradle.options`. This ensures the same signing configuration between developer machines.

* **Release key setup from properties** To have a reusable CI setup the release key can be specified using `releaseKeyStore_file`, `releaseKeyStore_password`, `releaseKeyStore_alias`, and `releaseKeyStore_keyPassword`. These can be set as gradle properties or specified [by environment variables](https://docs.gradle.org/current/userguide/build_environment.html), e.g. `ORG_GRADLE_PROJECT_releaseKeyStore_password`. 

* **`neverBuildRelease`** This option for flavors lets you exclude a flavor from release builds, e.g. your development or staging environments.

      productFlavors {
        development {
          ext.neverBuildRelease = true
        }
      }

* **APK renaming** We use a consistent naming for our artifacts:  
`{name}-{variant}{split}-vc{versionCode}-{versionName}`

* Exclude some common conflict files while with `packagingOptions` and prevent lint errors to break the build.

### Setup
* have `git` installed and on your path
* specify `keyStore` in your global `gradle.properties` with the path to a shared debug key
