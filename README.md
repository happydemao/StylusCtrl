# StylusCtrl


Maintainer: alpha-liu-01

StylusCtrl maps a drawing tablet's area, pressure curve, and side buttons on Android. The settings screen is a normal app. The Magisk module's `stylusctld` is what rewrites the input. The virtual device stays a pointer, and no IDC is installed.

> **This repository is a fork** of [alpha-liu-01/StylusCtrl](https://github.com/alpha-liu-01/StylusCtrl).
> It carries the 0.6.1 pressure-curve work: [release v0.6.1](https://github.com/happydemao/StylusCtrl/releases/tag/v0.6.1).
> The changes were prepared with AI assistance, see [AI-assisted changes](#ai-assisted-changes).


## Screenshots

<div align="center">
  <img src="https://i.imgur.com/nvPPpZ1.png" alt="Mapping" width="32%">
  <img src="https://i.imgur.com/b0jLhba.png" alt="Pressure" width="32%">
  <img src="https://i.imgur.com/bbTihJi.png" alt="Shortcuts" width="32%">
</div>

## New in 0.6.3

- **Language.** English stays the default in `values/`; Chinese lives in
  `values-zh/` and now covers every string. Wording for the internal pen was
  fixed: "接管内屏笔" instead of "抓取内屏笔", "作为指针设备上报" instead of
  "按指针送出", and the surrounding explanations were rewritten in plainer
  Chinese.

## New in 0.6.2

- **Chinese translations completed.** Every UI string now has a `values-zh/`
  entry, including the pressure-curve additions.
- **Modern navigation icons.** The rail uses outline vector icons
  (`res/drawable/nav_*.xml`) instead of the platform `ic_menu_*` bitmaps, and
  they follow the theme tint like the rest of the Material components.
- **Pressure fit, cleaned up.** The adjust-from-strokes dialog now samples pen
  strokes only (finger and mouse are ignored), drops zero-pressure hover
  samples, caps each stroke at 500 samples, and shows a live dashed preview of
  the fitted target plus stroke and sample counts. Applying needs at least
  3 strokes and 24 samples. The fit maths lives in `fitKnots`/`blend`, with the
  inverse of the current curve still applied first.
- **Undo and duplicate.** The Pressure page gained:
  - **Undo**: restores the previous curve state after a drag, add/remove point,
    reset or fit (20 steps deep, per pen).
  - **Duplicate**: copies the active curve and names it "<name> copy". New curve
    now starts from the default shape instead of copying the current one.

## New in 0.6.1 (this fork)

### Pressure curves with up to twelve knots

The curve is no longer a fixed four-point cubic. Every pen curve now holds
between 2 and 12 knots:

- Tap empty space in the graph to add a knot, drag to move it, drag an endpoint
  to move that deadzone. Add point / Remove point work too.
- App and daemon evaluate the same monotone cubic (PCHIP), so the preview is
  what the pen actually does, with no overshoot between knots.
- Old configs keep their feel: the legacy `pressure.curve` cubic is sampled into
  five knots on load.
- Config keys: `pressure.points` (flat x,y pairs), `pressure.deadzone`,
  `pressure.ceiling`.

### A curve bank per pen

External and internal pens each keep a bank of named curves:

- Chips above the graph switch the active curve; switching writes the config and
  the daemon hot-reloads it, no restart.
- Duplicate the current curve, rename it, or delete it. Up to 8 curves per pen.
- The whole bank lives in `pressure.curves`, so config files and presets carry it.

### Adjust pressure from strokes (CSP style)

Pressure page → **Adjust from strokes**:

- Draw three to five strokes at different weights on the dialog canvas.
- Sampled pressure percentiles (10/25/50/75/90) are spread over the full output
  range, giving more usable travel where you actually press.
- The canvas sees pressure **after** the daemon applied the current curve, so
  each sample is first run back through that curve (table inverse, a dipping
  curve is folded to its running maximum) and fitted in the raw domain.
  Repeated fits converge instead of stacking a bias per run.
- Strength blends the fitted curve with the current one instead of replacing it.
  The result is ordinary knots, editable by hand afterwards.

### Compatibility

- The new daemon reads `pressure.points` and still accepts the old
  `pressure.curve` cubic, so upgrading the module keeps existing configs valid.
- The new app writes `points`; an old daemon will ignore them. App and module
  should be flashed together.
- The config read limit was raised from 64 KiB to 256 KiB because the curve bank
  now lives inside `config.json`.

## Pressure curves (technical notes)

Each pen (external and internal) keeps a bank of named curves. The Pressure page
switches between them with the chips above the graph; the active curve is what
the daemon applies. Curves are saved inside `pressure.curves`, so a config file
carries the whole bank.

Knots are evaluated with a monotone cubic (PCHIP) in both the app and
`stylusctld`. Old `pressure.curve` cubics are sampled into five knots on load.

Adjust from strokes samples pressure through the current curve, converts it back
to raw pressure, then fits the percentiles. Strength blends the result with the
current curve instead of replacing it. The fitted curve becomes normal knots, so
it can be edited by hand afterwards.

## Build

```sh
tools/build.sh          # or tools/build-zig.sh when zig is installed
tools/pack-module.sh
tools/build-apk.sh
```

`tools/build-zig.sh` builds the two static daemons with `zig cc`, which also
works on macOS where the musl.cc toolchains are Linux binaries.

## Installation

`tools/pack-module.sh` writes `out/stylusctrl-arm64.zip` and `out/stylusctrl-arm.zip`. Flash the one that matches the device in Magisk and reboot. `tools/build-apk.sh` writes `out/stylusctrl-arm64.apk` and `out/stylusctrl-arm.apk`. The app has no native code, so either apk installs on either device. The zip is the one that has to match: it carries `stylusctld`.

A ready-made build of this fork is attached to
[release v0.6.1](https://github.com/happydemao/StylusCtrl/releases/tag/v0.6.1):
APKs, both module zips, the source archive and `SHA256SUMS`.

## AI-assisted changes

The work in this fork (commits after `7fcaa06`, the 0.6.1 pressure-curve
features and the fit fix) was produced by an AI coding agent
(DeepSeek Harness) operating on behalf of [@happydemao](https://github.com/happydemao).

- Upstream code and MIT licensing are unchanged; only the pressure curve stack
  (app UI, `Profile` config model, `stylusctld` curve evaluation) and the build
  scripts were touched.
- Verified: APK builds and passes signature/resource/version checks, both module
  zips pass integrity checks, the daemons cross-compile for aarch64 and armv7,
  and the curve math plus the config round-trip are covered by local tests.
- Tested on hardware: Samsung Galaxy Tab S7 (SM-T870), One UI 8.5.
- Still open: the daemon unit tests were compiled but not executed on Linux/ARM
  hardware in CI, and no other device or ROM has been tried yet.

### 中文说明

本仓库是上游的 fork，0.6.1 的多控制点压感曲线、按划线调整笔压、多曲线切换，
以及「采样先反解当前曲线」的修复，均由 AI（DeepSeek Harness）在
[@happydemao](https://github.com/happydemao) 的委托下完成。

- 已验证：APK 构建与签名/资源/版本检查、两个模块 zip 完整性、aarch64/armv7
  守护进程交叉编译、曲线数学与配置读写的本地测试。
- 已实机测试：三星 Galaxy Tab S7（SM-T870，One UI 8.5）。
- 仍未覆盖：守护进程单元测试未在 Linux/ARM 上执行，其它机型与 ROM 尚未测试。

## License

StylusCtrl is [MIT](LICENSE), copyright alpha-liu-01.

`magisk/stylusctrl/META-INF/com/google/android/update-binary` is Magisk's `module_installer.sh`, copied unchanged and still under GPL-3.0. See [NOTICE](NOTICE).
