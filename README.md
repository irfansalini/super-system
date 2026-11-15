# Super Remote – IR remote app for Android

Android app that uses your phone's built-in **IR blaster** to control:

* **LG TV** – full remote (power, input, numbers, arrows, OK, volume, channels, Netflix…)
* **Vinnfier HyperBar 3000** soundbar – power, volume, mute, inputs (BT / optical / AUX / HDMI ARC), EQ, bass/treble
* **Any other brand** – presets for Samsung, Sony and Philips TVs, or add a blank remote and type the codes
  (NEC / Samsung / Sony / RC5 address + command, Pronto hex, or raw timings), plus your own custom buttons.

## Download

Get **SuperRemote.apk** from the [latest release](../../releases/latest), open it on the phone and allow
“Install unknown apps” when Android asks.

## Vinnfier HyperBar 3000 codes

Vinnfier does not publish the IR codes, so the app starts with the codes most common for this class of soundbar
(NEC address `0x00`). If a button doesn't react:

1. Open **Code Finder** (home screen, or ⋮ in the soundbar remote).
2. Turn the soundbar on, point the phone at it, press **START SCAN** and watch it.
3. When it reacts (volume/input/power/display change) press **IT REACTED**, step back with ◀ / SEND to the exact code.
4. **SAVE** it to a button. Other buttons on the same remote almost always use the same address, so after the
   first hit only the command needs scanning. ⋮ → *Change address of all buttons* applies a found address to every button.

Tip: if the soundbar is on HDMI ARC, enable **SIMPLINK (HDMI-CEC)** on the LG TV and the TV remote's volume controls it too.

Back up your found codes with ⋮ → *Backup all devices to clipboard* (restore via *Add device → Import*).

## Building

Pushes build the APK automatically with GitHub Actions (`.github/workflows/build-apk.yml`).
Locally: `./gradlew assembleRelease` (needs the Android SDK).
