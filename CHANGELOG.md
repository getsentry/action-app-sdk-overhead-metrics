# Changelog

## Unreleased

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
