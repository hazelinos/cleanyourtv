# Remote buttons (in-app remapper)

Launcher settings → **Remote buttons** (also a row in the quick settings panel) lets the TV's
owner give spare remote buttons a job on Home: press **Add mapping**, press the button, then pick
what it does — open quick settings, open Inputs, open the app library, show the clock, go back to
the top of Home, or open an app. Mappings can be changed or deleted from the same page, and
**Reset all mappings** clears them. With no mappings saved — the default — every button behaves
exactly as before.

## What it can and cannot do

- **In-app only.** A mapping runs while CleanYourTV Home is in front, and nowhere else. It is
  enforced by Home's own key dispatch; nothing is filtered system-wide, no accessibility service
  is added and no permission is needed.
- **One mapping per button.** Giving a mapped button a new action replaces the old one; the page
  says so when that happens.
- **Some buttons can never be mapped.** Home, Assistant, power, volume, channel, the Settings
  button this launcher already answers, and the navigation keys (D-pad, OK, Back, Menu) are kept
  by Android, the firmware or Home's own navigation. The page refuses them with an explanation
  instead of saving a mapping that could never fire — a button the firmware never delivers to
  apps cannot be detected here either.
- **Global remapping is a different feature.** Buttons that must work inside other apps are the
  `app_keys` list in [themes/README.md](../themes/README.md), answered by the opt-in
  InputKeyService accessibility helper. The remapper deliberately does not widen that.

Mappings live in `remote_mappings.json` in the launcher's private storage, written atomically
and validated strictly on read: the atomic write and the move-aside follow the cards.json
pattern, the parsing is org.json as in Config. A file that does not decode is moved aside and
Home continues with no mappings.
