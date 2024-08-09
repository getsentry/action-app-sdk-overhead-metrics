# Changelog

## Unreleased

### Dependencies

* Update Gradle version from `7.5.1` to `8.9` ([#16](https://github.com/getsentry/action-app-sdk-overhead-metrics/pull/16))
* Update Java version from `11` to `17` ([#17](https://github.com/getsentry/action-app-sdk-overhead-metrics/pull/17))
* Update Kotlin version from `1.7.10` to `2.0.10` ([#18](https://github.com/getsentry/action-app-sdk-overhead-metrics/pull/18))
* Update internal dependencies ([#20](https://github.com/getsentry/action-app-sdk-overhead-metrics/pull/20))

## 1.6.0

### Fixes

* Update Appium version used in Sauce Labs from EOL `2.0.0` to `appium2-20240701` ([#13](https://github.com/getsentry/action-app-sdk-overhead-metrics/pull/13))

## 1.5.0

### Features

* Improve log output for StartupTimeTest. ([#7](https://github.com/getsentry/action-app-sdk-overhead-metrics/pull/7))

### Dependencies

* Update `action/checkout`, `action/setup-java` and `action/upload-artifact` to the latest versions due to NodeJS 16 deprecation in actions.

## 1.4.1

### Fixes

* Don't fail on previous results download if an artifact is expired.

## 1.4.0

### Changes

* Replace [deprecated](https://github.blog/changelog/2022-10-11-github-actions-deprecating-save-state-and-set-output-commands/) set-output with writing to `GITHUB_OUTPUT`.

### Dependencies

* Update `action/upload-artifact` to v3 to prepare for GHA [dropping Node12 support](https://github.blog/changelog/2022-09-22-github-actions-all-actions-will-begin-running-on-node16-instead-of-node12/).

## 1.3.0

### Features

* Collect and print previous data on the same branch and data from the main branch.

## 1.2.0

### Features

* Retry startup time collection if standard deviation of the collected data is too high.

### Fixes

* Support running with URL-based app paths on Windows (avoid `Path.of()` if path contains ':').

## 1.1.0

### Features

* Add name input argument.
* Use Samsung Galaxy S22 as the default Android device due to better availability on SauceLabs.

### Fixes

* SauceLabs recently limited logcat access to 10k items.

## 1.0.1

### Fixes

* Select required Java SDK version automatically.

## 1.0.0

Initial release with iOS and Android support for app size & startup time measurements.
