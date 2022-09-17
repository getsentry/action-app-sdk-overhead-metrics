## Unreleased

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
