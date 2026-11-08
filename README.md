# StylusCtrl


Maintainer: alpha-liu-01

StylusCtrl maps a drawing tablet's area, pressure curve, and side buttons on Android. The settings screen is a normal app. The Magisk module's `stylusctld` is what rewrites the input. The virtual device stays a pointer, and no IDC is installed.


## Screenshots

<div align="center">
  <img src="https://i.imgur.com/nvPPpZ1.png" alt="Mapping" width="32%">
  <img src="https://i.imgur.com/b0jLhba.png" alt="Pressure" width="32%">
  <img src="https://i.imgur.com/bbTihJi.png" alt="Shortcuts" width="32%">
</div>

## Pressure curves

Each pen (external and internal) keeps a bank of named curves. The Pressure page
switches between them with the chips above the graph; the active curve is what
the daemon applies. Curves are saved inside `pressure.curves`, so a config file
carries the whole bank.

The editor accepts up to twelve knots. Tap empty space in the graph to add one,
drag to move it, or use Add point / Remove point. Knots are evaluated with a
monotone cubic (PCHIP) in both the app and `stylusctld`, so the preview is what
the pen does. Old `pressure.curve` cubics are sampled into five knots on load.

Adjust from strokes is the CSP-style helper: draw a few strokes at different
weights, and the sampled pressure percentiles (10/25/50/75/90) are spread over
the output range. Strength blends the result with the current curve instead of
replacing it. The fitted curve becomes normal knots, so it can be edited by hand
afterwards.

Note: `pressure.points` is read by the daemon in this release. A config that
only has `points` needs the matching `stylusctrl-*.zip` module; the app alone
cannot apply it on an older daemon.

## Build

```sh
tools/build.sh
tools/pack-module.sh
tools/build-apk.sh
```

## Installation

`tools/pack-module.sh` writes `out/stylusctrl-arm64.zip` and `out/stylusctrl-arm.zip`. Flash the one that matches the device in Magisk and reboot. `tools/build-apk.sh` writes `out/stylusctrl-arm64.apk` and `out/stylusctrl-arm.apk`. The app has no native code, so either apk installs on either device. The zip is the one that has to match: it carries `stylusctld`.

## License

StylusCtrl is [MIT](LICENSE), copyright alpha-liu-01.

`magisk/stylusctrl/META-INF/com/google/android/update-binary` is Magisk's `module_installer.sh`, copied unchanged and still under GPL-3.0. See [NOTICE](NOTICE).
