# Pocket Pet Android v0.4 – Living Pet

Child-friendly offline digital-pet prototype. The pet starts as an Egg and grows through Pichu → Pikachu → Raichu, with an optional Mega form in this internal fan prototype.

## v0.4 interaction upgrades
- Egg wiggle/crack/hatch flow
- Tap = head pat, swipe = pet, hold = cuddle
- Feed: choose food, then drag it onto the pet
- Play: throw/drag a ball and the pet chases it; Peekaboo and Dance remain simple
- Bath: swipe over the pet 3 times; bubbles are shown
- Sleep animation + refreshed wake-up
- Gentle autonomous walking/idle movement
- Evolution flash overlay for hatch/evolution/Mega unlock
- Friendly optional battle; no injury/death language
- Offline save using SharedPreferences
- No Internet permission, ads, login, public chat, loot box, or pet death

## Build
Open this folder in Android Studio and build the `app` module. This repository intentionally uses programmatic drawing/placeholders rather than shipping official Pokémon artwork.

## Prototype thresholds
- Egg → Pichu: 4 gentle care interactions
- Pichu → Pikachu: Lv.3 + Bond 15
- Pikachu → Raichu: Lv.6 + Bond 30
- Raichu → Mega choice: Lv.10 + Bond 50

The thresholds are deliberately fast for playtesting.


## v0.6 Motion & Forest Walk
- More organic pet motion: breathing, squash/stretch, lean, ear/tail follow-through and reaction animations.
- Larger food cards, food tokens and ball touch targets for children.
- Forest Walk is now a playable 30-second parallax scene with collectible goodies and jump obstacles.
- Forest rewards feed back into EXP, Happy, Bond and Energy.
