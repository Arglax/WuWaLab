# WuWa Lab

> An unofficial Wuthering Waves companion for Android — track your Waveplates, plan your pulls, and see exactly where every Astrite goes.

## Overview

WuWaLab is a free, offline companion and tracker app for Wuthering Waves, built natively with Kotlin and Jetpack Compose (Material 3). It watches the clock so you don't have to: Waveplate regeneration, Crystal overflow, Lunite Pass check-ins, Astrite income and spending, convene odds, and your day-to-day planning — all from your home screen, a floating bubble, or the app itself.

> [!IMPORTANT]
> WuWaLab ships as a side-loaded `.apk` — **Android only.** There is no iOS version and none is planned, since iOS doesn't allow home-screen widgets or floating overlays of the kind this app is built around.

[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?style=plastic&logo=discord&logoColor=white)](https://discord.gg/renjxYBEZM)
[![Latest Release](https://img.shields.io/github/v/release/Arglax/WuWaLab?style=plastic&logo=github&logoColor=white&label=Release&color=blue)](../../releases)

**It never touches the game.** WuWaLab does not log into your account, read game files, automate anything, or talk to Kuro Games' servers. Everything you see is something you told it.

> [!CAUTION]
> The app is still in its early development and testing phase. Bugs may be prevalent.

## Disclaimer

> [!NOTE]
> WuWaLab is an unofficial, community-made fan project and is **NOT** affiliated with, endorsed by, or sponsored by Kuro Games.
>
> This application is provided free of charge, contains no advertisements, and is intended solely as an offline companion utility for the Wuthering Waves community.
>
> All trademarks, game assets, artwork, character names, logos, and other intellectual property referenced or displayed within this project are the property of their respective owners. If you are the rights holder and believe any content should be removed or credited differently, please contact me and I will promptly address the issue.
>
> The app explains all of this itself the first time you open it, via a one-time "How WuWaLab Works" dialog — tick the checkbox there if you'd rather not see it on future launches.

## Table of Contents

* [Overview](#overview)
* [Disclaimer](#disclaimer)
* [Screenshots](#screenshots)
* [Getting Started / Installation](#getting-started--installation)
* [Finding Your Way Around](#finding-your-way-around)
* [Permissions](#permissions)
* [Features](#features)
* [The Two Currencies](#the-two-currencies)
* [Titles & Rarity](#titles--rarity)
* [FAQ](#faq)
* [Support](#support)

---

## Screenshots
<details>

### The essentials

| Dashboard | Astrite Tracker | Home Widget |
| :---: | :---: | :---: |
| <img src="img/dashboard.jpg" width="200" alt="Dashboard" /> | <img src="img/astrite_tracker.jpg" width="200" alt="Astrite Tracker" /> | <img src="img/widget_landscape.jpg" width="200" alt="Home Widget" /> |

### Planning your pulls

| Pull Planner | Probability Curve | Convene Log |
| :---: | :---: | :---: |
| <img src="img/pull_planner.jpg" width="200" alt="Pull Planner" /> | <img src="img/pull_planner_curve.jpg" width="200" alt="Probability Curve" /> | <img src="img/convene_log.jpg" width="200" alt="Convene Log" /> |

### The economy view

| Economic Dashboard | Logbook | App Shop |
| :---: | :---: | :---: |
| <img src="img/economic_dashboard.jpg" width="200" alt="Economic Dashboard" /> | <img src="img/logbook.jpg" width="200" alt="Logbook" /> | <img src="img/app_shop.jpg" width="200" alt="App Shop" /> |

### Personalization

| Widget Studio | Profile Studio | Event Detail Popup |
| :---: | :---: | :---: |
| <img src="img/widget_compact.jpg" width="200" alt="Widget Studio" /> | <img src="img/profile_studio.jpg" width="200" alt="Profile Studio" /> | <img src="img/event_detail_popup.jpg" width="200" alt="Event Detail Popup" /> |

### Extras

| Floating overlay | Eisenhower Matrix | Landscape layout |
| :---: | :---: | :---: |
| <img src="img/overlay_bubble.jpg" width="200" alt="Floating overlay" /> | <img src="img/matrix.jpg" width="200" alt="Eisenhower Matrix" /> | <img src="img/landscape_dashboard.jpg" width="200" alt="Landscape layout" /> |

---
</details>

## Getting Started / Installation

> [!IMPORTANT]
> **Minimum Requirements**
> * **OS:** Android 11 or higher (minSdk 30, targetSdk 37) — **Android devices only**, this is a side-loaded `.apk`, not a Play Store or iOS app.
> * **Storage:** At least 50MB of free space
> * **Permissions:** See [Permissions](#permissions) — most are optional and only needed if you use that specific feature.

### Install steps

1. Go to the [Releases](../../releases) page and download the latest `.apk` (e.g. `wuwalab_alpha-<version>.apk`).
2. On your Android device, tap the downloaded APK to begin installation.
   * First time installing from outside the Play Store? Android will ask permission — tap **Settings** on the prompt, enable **Allow from this source**, go back, and tap the APK again.
   * Sometimes if downloads get stuck, you have to open your default browser/google chrome and then manually click on the download.  
3. Tap **Install**, then **Open**.
4. Read the one-time **"How WuWaLab Works"** dialog, then tick "Don't show this again" or just tap **Got it**.
5. Set your **IGN** and **Union Level**, and enter your current **Waveplates** and **Crystals** so the math starts from the right place.
6. *(Optional)* Allow notifications so "Notify Me" alerts can reach you.
7. *(Optional)* Open **Extras → Overlay** to turn on the floating bubble, or tap **Add Widget to Home Screen**.

### Staying updated

The app checks GitHub for new releases on its own, tells you when one exists, and can download and install it for you. Pull-to-refresh on the Dashboard checks immediately.

---

## Finding Your Way Around

Everything used to sit in one long row of tabs. It's now grouped into sections, so you tap a **group** first and only that group's pages appear beneath it. Fewer things on screen, nothing hidden.

| Group | What's inside | What it's for |
| --- | --- | --- |
| **Home** | Dashboard | Waveplates, Crystals, daily sign-in, events, at-a-glance totals. |
| **Economy** | Astrite Tracker, Pull Planner | Everything about Astrites — earning them, and spending them on convenes. |
| **Planning** | Matrix, To-Do | Your own goals and chores, unrelated to currency. |
| **Earn** | Daily Quiz | A once-a-day arithmetic quiz that pays out Argstrites. |
| **Extras** | Overlay, App Shop, Redeem, Widget Studio, Profile Studio | The floating bubble, cosmetics you buy with Argstrites, promo codes, and the two Studios. |

You can still swipe left and right anywhere to move between pages — the tabs and the swipe stay in sync. Both rows adapt automatically: they share the width evenly in landscape and scroll sideways like chips in portrait.

---

## Permissions

WuWaLab only requests permissions tied to specific optional features — nothing here is used for tracking, ads, or telemetry.

| Permission | Why it's needed |
| --- | --- |
| Internet / Network State | Fetches live event banners(to be implemented, currently hardcoded) and checks for app updates on GitHub. |
| Post Notifications | Powers "Notify Me" alerts (waveplates full, crystals maxed, custom thresholds, Lunite Pass reminders, event countdowns, unclaimed Argstrite reminders) and overlay logging confirmations. |
| Schedule Exact Alarm | Fires the daily Lunite Pass check-in reminder at the right Server Time reset. |
| Display Over Other Apps | Required only if you enable the Floating Overlay bubble. |
| Request Install Packages | Lets the in-app update checker install a newly downloaded APK directly. |
| Receive Boot Completed | Re-schedules reminders and alarms after your device restarts. |
| Foreground Service | Keeps the Overlay bubble alive while it's active. |

---

## Features

### Feature Overview

| Feature | Description |
| --- | --- |
| **Resource Tracking** | Waveplates and Crystals with accurate, game-accurate regeneration and caps. |
| **Astrite Tracker** | Log what you earn, see a 7-day chart, watch your running total. |
| **Economic Dashboard** | Optional advanced view: balance line graph, full transaction logbook, earning vs spending breakdown. |
| **Pull Planner** | Convene odds from your real pity, a "how far am I" progress card, and a permanent convene log. |
| **Hidden Argstrite Bonus** | Logging anything with an optional note earns a couple of extra Argstrites — a small nudge to build the habit of writing things down. |
| **Daily Earn Quiz** | A once-a-day, 5-question arithmetic quiz (basic ops through order-of-operations) that pays out Argstrites based on how many you get right. |
| **Redeem Codes** | One-time promo codes for bonus Argstrites and exclusive titles. |
| **App Shop** | Spend Argstrites on profile portraits, widget backgrounds, and titles with their own rarity tiers. |
| **Titles** | Equip a rarity-colored title next to your name — earned via the Shop or a Redeem code. |
| **Widget Studio** | Upload your own photo, frame it, preview it, and set it as your widget background. |
| **Profile Studio** | Upload your own photo, frame it into a circle, and set it as your profile picture — re-framing the same photo later is free. |
| **Lunite Pass Support** | Daily check-ins, reminders, and totals. |
| **Floating Overlay** | Draggable bubble for quick Astrite logging (both earning and spending) over other apps, screen-size aware on rotation. |
| **Home Widget** | Live Waveplate countdown on your home screen, with a background you can change. |
| **Planners** | Pull Planner, To-Do list, and an Eisenhower Matrix. |
| **Grouped Navigation** | Tidy sections instead of one crowded tab row. |
| **Event Detail Popups** | Live and Ended events only, sortable by time remaining, color-coded by category, tap for a full breakdown — driven entirely by a bundled/remote `events.json`. |
| **Full Reset** | A Settings option that wipes all local app data and restarts fresh, in case you ever want a clean slate. |

<details>
<summary><strong>Full feature list (click to expand)</strong></summary>

#### Resource Tracking (Waveplates & Crystals)

* Accurate passive regeneration: **+1 Waveplate every 6 minutes**, **+1 Crystal every 12 minutes**.
* Respects the game's real limits — Waveplates soft-cap at 240 (with a manual override up to 2400 for overloaded states), Crystals hard-cap at 480 and can never be pushed past it.
* Live status indicators: Depleted, Regenerating, Full, Overloaded, plus a "Frozen" state for Crystals while Waveplates haven't capped yet.
* Manual entry cards for both, so you can correct the count any time you spend or gain in-game. Every manual update quietly earns a small Argstrite reward too — see [Hidden Argstrite Bonus](#hidden-argstrite-bonus-how-argstrites-are-earned-from-logging) below.

#### Astrite Tracker — Simple mode

The original, lightweight view, completely unchanged:

* Log the Astrites you earn each day, with an optional note for where they came from.
* A 7-day bar chart on the Dashboard, plus weekly, monthly, and lifetime totals.
* Daily, weekly and monthly chart periods.

#### Economic Dashboard — Advanced mode

A switch at the top of the Astrite page flips between **Simple** and **Advanced**. Advanced is off by default, your choice is remembered, and both views read exactly the same saved data — switching back and forth never changes or loses anything.

* **Balance line graph** over 7, 30 or 90 days, with green earning bars and red spending bars underneath, and a dot marking today's live balance.
* **Full logbook** — every single transaction, newest first, with its date, category, note and amount. Convene spends logged from the Pull Planner show the banner, pull count, and the pity you were on at the time. Spends logged from the Overlay bubble show up here too, under "Overlay Quick Spend".
* **Add Transaction** — log an earning or a spend by hand, pick a category (Daily Login, Event, Quests, Convene, Shop…), and add a note.
* **Breakdown grid** — earned, spent and net figures for this week and this month, your average earned and spent per day, and an estimate of how many days until your next pull.
* Swipe-free deletion: remove any row you logged by mistake.

**About the numbers — the rules that never bend:**

* **Astrites Earned** is a lifetime total that only ever goes up. It can never be negative, no matter how much you convene.
* **This week / this month** figures are *net* (earned minus spent). Those **can** go negative — if you spent more than you collected, that genuinely happened, and the tracker says so instead of hiding it.
* **Your daily average is built from earnings only**, so a big convene session never drags it below zero. Spending has its own separate average.
* **Your balance can never go below zero.** Every spend is checked against what you actually hold, in the planner, the shop, the overlay, and the manual entry form alike.

Every number on this page is the same number the Dashboard, the Profile header and the Pull Planner show. There is one source of truth, so nothing can quietly disagree.

#### Hidden Argstrite Bonus (how Argstrites are earned from logging)

Every time you write an Astrite log entry, a spend/earn entry, a Pull Planner convene, a To-Do item, or a manual Waveplate update, you quietly earn **+1 Argstrite** — and **+2** instead if you also filled in the optional note/description field on that entry.

The first time this ever happens, and the first time the note-bonus ever happens, WuWaLab shows a one-time popup explaining the mechanic ("Great! You found a hidden method to earn Argstrites"). After that, it stops interrupting you — the Argstrites keep coming every time, just silently, so logging stays fast.

**Why it works this way:** the reward isn't really about the Argstrites. It's a small, low-stakes incentive to build the habit of actually writing down *what* something was for, not just *how much*. Consistent notes are what make a log worth anything later — for spotting patterns, for catching where your Astrites actually went, or just for your own peace of mind. A logbook you can trust is a genuinely useful accountability habit, in-game or out, and the bonus is there to nudge you toward keeping one.

#### Daily Earn Quiz

* Found under the **Earn** group.
* 5 randomly generated arithmetic questions each day — plain operations up through order-of-operations (PEMDAS) chains, deliberately nothing beyond that (no exponents, no roots).
* One attempt per day, resetting at the same Server Time (4:00 AM Manila) boundary as everything else daily in the app.
* Payout scales with how many you get right: 1 correct = 5 Argstrites, 2 = 10, 3 = 20, 4 = 30, all 5 = 50.
* Come back after reset for another shot — your questions and score for the day are saved, so leaving and reopening the app mid-attempt won't reroll new questions on you.

#### Redeem Codes

* Found under **Extras → Redeem**.
* A simple textbox: type a code, tap Redeem.
* Every code works **exactly once per device** — reusing one just tells you it's already been claimed.
* Codes can grant Argstrites outright or unlock an exclusive title you can't get any other way.

#### App Shop

* Opened by the new **cart button** in your profile header, right beside the edit pencil, or from **Extras → App Shop**.
* Spends **Argstrites only** — the app's own currency. Your real Astrite convene budget is never touched.
* **Profile pictures — 5 Argstrites each.** Equip one and it replaces the portrait in your header.
* **Widget backgrounds — 10 Argstrites each.** Equip one and the artwork behind your home-screen widget changes immediately.
* **Titles — individually priced**, each with its own rarity tier and color. See [Titles & Rarity](#titles--rarity).
* **Sort** by price (low to high or high to low), by name, or unowned-first. **Filter** by category.
* Owned items switch from *Buy* to *Equip*, and equipping one shows a green check on its tile. You can unequip at any time to go back to the free default.
* You can't overspend. If an item costs more than you hold, the button is disabled and tells you exactly how many Argstrites you're short. A second check runs inside the purchase itself, so even a double-tap can't push you negative.
* Argstrites aren't only from the Daily Sign-In anymore — see [The Two Currencies](#the-two-currencies) for every way to earn them.

#### Lunite Pass Management

* Toggle Lunite Pass tracking on or off to fold in your daily +90 Astrites.
* Reminder notifications scheduled around the real Server Time reset (4:00 AM Manila / UTC+8).
* The Daily Sign-In card grants a flat **+10 Argstrites** once per game day, and automatically folds in the Lunite Pass's **+90 Astrites** when it's active — one tap, one confirmation showing both rewards side by side.

#### Notify Me / Alerts

* Push notifications when Waveplates hit full, Crystals hit the 480 cap, or a custom Waveplate threshold you choose is reached.
* Event reminders at 3 days and 1 day before an event window closes.
* A gentle nudge if you leave the app while you still have unclaimed Argstrites sitting in your pending balance, so a good logging streak never quietly goes uncollected.
* Debounced, so you're notified once per threshold crossing rather than repeatedly.
* Every notification carries a small category header ("WuWaLab · Waveplates", "WuWaLab · Overlay", "WuWaLab · Events", "WuWaLab · Argstrites", etc.) above the title, so it's obvious at a glance what triggered it before you even open it.

#### Floating Overlay Bubble

* A bubble that hovers over other apps, including the game itself — now shown as the WuWaLab logo, so it's instantly recognizable among other floating bubbles.
* Tap it for a quick logging popup without opening the full app, with two modes:
  * **Add** — bumps today's earned Astrite total, exactly like before.
  * **Log Spend** — writes a real SPEND entry through the same ledger the Pull Planner and Shop use, so it shows up in the Economic Dashboard logbook under "Overlay Quick Spend" rather than just silently subtracting a number.
* Both modes share the same quick-amount chips (+10 / +60 / +100 / +160) and both append their own kind of entry.
* Press and drag to move it; drag to the bottom-centre (it glows red with a bin icon) to dismiss it.
* A confirmation notification appears after logging (or closing without logging), then the popup closes itself.
* An **Add Widget** shortcut pins the home-screen widget in one tap on Android 8+.

#### Profile

* Set your In-Game Name and Union Level, and pick from the free bundled avatars (Default, Rover, Beacon), any portrait you've bought in the App Shop, or a custom photo from the Profile Studio.
* Equip a **title** from the Shop or a Redeem code, and it shows right under your name, colored by its rarity.
* Tap the header for a read-only stats card explaining both currencies.
* Two buttons at the end of the header: the **edit pencil** on the left, the **shop cart** on the right.
* Layout adapts to orientation — portrait keeps your name on its own row so a long IGN never misaligns the stats, landscape lays everything out in a single line with full labels.

#### Widget Studio

* Found under **Extras → Widget**.
* **Upload any photo** (PNG, JPG, or JPEG) from your device and frame it — pinch to zoom, drag to reposition, or use the zoom and position sliders. A reset button puts it back to centre.
* **See it before you buy it.** Two live previews show exactly how it will look as a wide 4x2 widget and as a square 2x2 one, including the real dimming the widget applies.
* Applying a custom background costs **20 Argstrites**, and you're asked to confirm first because purchases are final. If the image can't be processed, nothing is charged.
* **First visit gives you 20 Argstrites free**, with a popup explaining why: a background costs 20, and earning that from daily sign-ins alone would take two days before you could even try the feature. It's a one-time starter.
* Reverting to the default artwork is free — only applying costs anything.
* Your photo never leaves your phone. It's copied into the app's private storage and flattened into a single image the widget draws.

#### Profile Studio

* Found under **Extras → Profile Studio** — the square sibling of the Widget Studio, built for your profile picture instead of your home-screen widget.
* **Upload any photo** (PNG, JPG, or JPEG) and frame it — pinch to zoom, drag to reposition, or use the sliders — into a circular preview that matches exactly how it'll look in your profile header.
* Applying a **new** photo costs **20 Argstrites**, same as the Widget Studio.
* **Re-framing is free.** If you just want to zoom, reposition, or otherwise re-touch the *same* photo you already applied, re-applying it costs nothing — you're only ever charged again for a genuinely different upload. The confirmation dialog and button both make it explicit whether a given apply will be free or charged before you tap it.
* Reverting to a bundled/shop avatar is free.
* Your photo never leaves your phone — same private, on-device storage approach as the Widget Studio.
* Uploading a new photo now shows up everywhere immediately — the profile header and profile summary always reflect whatever you most recently applied.

#### Home Screen Widget

* Waveplates with a live "Full in Xh Ym" countdown, plus Crystals, Union Level and your Astrite total.
* Background artwork that respects whatever widget skin you've equipped in the shop, falling back to the bundled art.
* Full landscape size shows the art on the right and darkens the left where the numbers sit; small and square sizes use an even scrim so every value stays readable.
* **Tapping the widget opens a quick chooser** rather than dumping you on whatever page you last used — jump straight to the Dashboard, the Pull Planner, or your To-Do list, or set an alarm in your phone's own clock app.
* Refreshes every 30 minutes in the background, and instantly whenever you change a value in-app.

#### Planning Tools

* **To-Do list** with reminders — creating a new task also quietly earns a small Argstrite reward, same as any other log.
* **Eisenhower Matrix** for sorting tasks by urgency and importance.
* Both live under the Planning group, away from the currency pages.

#### Sound & Haptic Feedback

* Taps play the system click sound with a light haptic tick, using the platform's own sounds rather than bundled audio — so it automatically respects your volume, silent mode and "touch sounds" setting.

#### Event Tracking

* **Live** and **Ended** tabs pulled from a GitHub-hosted events cache, refreshed in the background.
* **Sort toggle** flips the list between soonest-ending-first and latest-ending-first.
* Each banner's border is color-coded by category at a glance: green for Leisure, blue for Farming, red for Combat, amber for everything else (Convene, Featured, Permanent).
* **Tap any event banner** to open a detail popup: its category, its full countdown/expiry, and a bullet-point breakdown of what's actually in it (drop rates, featured characters, claimable rewards, farmable Astrite totals, and so on).
* Everything shown — name, dates, category, banner art, and the detail bullets — is read straight out of `assets/events.json` (or its GitHub-hosted counterpart), so adding or editing an event is a JSON edit, not a code change. See that file for the exact shape.

#### Full Reset

* Found in **Settings → Danger Zone**.
* Wipes every piece of local data WuWaLab has ever saved — balances, logs, purchases, titles, redeemed codes, custom photos, everything — and restarts the app as if freshly installed.
* Gated behind a two-step confirmation, since it can't be undone. Functionally identical to clearing the app's storage from Android's own Settings, just reachable without leaving the app.

#### Update Checker

* Notifies you of new GitHub releases and can download and install them directly, with your permission.

</details>

---

## The Two Currencies

They're easy to mix up, so here's the short version:

| | **Astrites** | **Argstrites** |
| --- | --- | --- |
| What is it | The game's real convene currency | WuWaLab's own in-app currency |
| Where it comes from | You log what you earn in-game | Daily Sign-In, the Daily Earn Quiz, Redeem codes, and small bonuses for logging with notes (see below) |
| What it buys | Convenes, tracked in the Pull Planner | Profile pictures, widget backgrounds, and titles in the App Shop, plus custom photo uploads in the Widget Studio and Profile Studio |
| Can it go negative | No — the balance floors at zero | No — purchases are blocked before that can happen |

The two pools never touch each other. Buying a portrait can't shrink your pull budget, and convening can't cost you a widget skin.

**Every way to earn Argstrites:**

* **Daily Sign-In** — a flat +10, once per game day.
* **Daily Earn Quiz** — 5–50, once per game day, based on how many arithmetic questions you get right.
* **Redeem codes** — fixed amounts, each code once per device.
* **Logging with a note** — a small +1 (or +2 with a note) for every Astrite log, spend/earn log, Pull Planner convene, To-Do item, and manual Waveplate update. See [Hidden Argstrite Bonus](#hidden-argstrite-bonus-how-argstrites-are-earned-from-logging) for why this exists — it's less about the currency and more about training the habit of consistent, note-worthy logging.

---

## Titles & Rarity

Titles are cosmetic, shown right under your name, and come in five rarity tiers:

| Rarity | Color |
| --- | --- |
| Common | Gray |
| Uncommon | Green |
| Rare | Purple |
| Epic | Gold |
| Legendary | Pulsating red glow |

Titles are unlocked two ways: buying one in the **App Shop** (each has its own individual price, separate from the flat per-item pricing of portraits and backgrounds), or unlocking an exclusive one through a **Redeem code**. Once owned, equip it from the Shop and it'll show on your Profile header and profile summary everywhere.

---

## FAQ
<details>
**Is this against the Terms of Service?**  
No. WuWaLab never reads game memory or files, never logs into your account, and never automates anything. It's a notepad with good maths.

**Why do I have to type my values in manually?**  
Because there is no sanctioned way to read live values out of the game. Manual entry is the honest, safe option.

**Is there an iOS version?**  
No, and there are no plans for one. WuWaLab ships as a side-loaded Android `.apk` — the floating overlay and home-screen widget it's built around aren't things iOS allows third-party apps to do.

**How do I actually earn Argstrites?**  
Four ways: the Daily Sign-In (+10/day), the Daily Earn Quiz under the Earn tab (5–50/day depending on your score), one-time Redeem codes, and a small +1/+2 bonus every time you log something — an Astrite entry, a spend, a Pull Planner convene, a To-Do item, or a manual Waveplate update. See the next question for why that last one exists.

**Why does logging give me Argstrites — is that just a gimmick?**  
Partly, but there's a real reason behind it: the bonus is bigger when you fill in the optional note/description field, not just the amount. That's deliberate. A log entry that just says "+500" tells you nothing a week later; a log entry that says "+500, event login" is one you can actually learn from. The Argstrite bonus is a small nudge to build that habit — writing down *what* something was for, not just *how much* — because that's what makes a logbook genuinely useful for spotting patterns, tracking where things go, or just holding yourself accountable. It's meant to train the same instinct that makes any accounting or tracking habit actually work, in-game or out.

**Do I have to claim my Argstrites right away?**  
No, they sit in a pending balance until you claim them. If you leave the app with some still unclaimed, WuWaLab will send a quick reminder notification so a good logging streak doesn't quietly go uncollected.

**Can I redeem the same code twice?**  
No — every code works exactly once per device. Redeeming it again just tells you it's already been claimed.

**What happens if I use Full Reset?**  
Everything gets wiped — balances, logs, purchases, titles, redeemed codes, custom photos, settings, all of it — and the app restarts as if freshly installed. It's gated behind a two-step confirmation because it can't be undone.

**My weekly Astrites show a negative number. Is that a bug?**  
No — that's the net figure for the week, and if you convened more than you collected, it's supposed to be negative. Your *lifetime earned* total and your *daily average* never go negative.

**Will I lose my data if I switch to Advanced mode?**  
No. Both views read the same saved data. Switch as often as you like.

**Can I get my Astrites back after deleting a convene log row?**  
Deleting a row removes it from the log only — it doesn't refund the spend. Use the Add Transaction form if you need to correct a balance.

**I already applied a photo in Profile Studio. Why wasn't I charged again when I re-applied it?**  
Because it's the same photo — only re-positioned or re-zoomed. Profile Studio only charges Argstrites the first time a given photo is applied; re-framing and re-applying that same photo afterwards is free. Uploading a genuinely different photo will cost Argstrites again.
</details>

---

## Support

If you hit a bug or have a feature request, please open an issue in this repository with a detailed description of what happened and what you expected.

---

##   Tags for SEO Indexing:

<details>
 #wuwa #wuwalab #wuwa-lab #android #utility #wuwautility #wuwalabapp #wuwalabapk #wutheringwaves #wuwacompanion #astritetracker #pullplanner
</details>
