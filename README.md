# WuWa Lab

## Overview

WuWaLab is an unofficial Wuthering Waves companion and tracker app for Android. Built natively using Kotlin and Jetpack Compose (Material 3), this single-module application helps you monitor your Waveplates, Astrites, Lunite Pass check-ins, and more, right from your home screen or through a convenient floating overlay.
>[!CAUTION]
>The app is still in its early development and testing phase, bugs may be prevalent.  

## Disclaimer
>[!NOTE]
>WuWaLab is an unofficial, community-made fan project and is NOT affiliated with, endorsed by, or sponsored by Kuro Games.
>
>This application is provided free of charge, contains no advertisements, and is intended solely as an offline companion utility for the Wuthering Waves community.
>
>All trademarks, game assets, artwork, character names, logos, and other intellectual property referenced or displayed within this project are the property of their respective owners. If you are the rights holder and believe any content should be removed or credited differently, please contact me and I will promptly address the issue.


## Getting Started

> [!IMPORTANT]
> **Minimum Requirements:**
> * **OS:** Android 11 or higher (minSdk 30)
> * **Storage:** At least 50MB of free space  

To get started, download the latest APK from the Releases page and install it on your device. The app includes a built-in update checker that will notify you and download future GitHub releases automatically.

## Table of Contents

* [Overview](https://www.google.com/search?q=%23overview)
* [Getting Started](https://www.google.com/search?q=%23getting-started)
* [Features](https://www.google.com/search?q=%23features)
* [Support](https://www.google.com/search?q=%23support)
* [Disclaimer](https://www.google.com/search?q=%23disclaimer)

---

## Features

### Feature Overview

| Feature | Description |
| --- | --- |
| **Resource Tracking** | Track Waveplates and Crystals with accurate, game-logic caps. |
| **Astrite Logger** | Log daily Astrite earnings and view 7-day progress charts. |
| **Lunite Pass Support** | Daily check-ins, custom reminders, and total gathered stats. |
| **Floating Overlay** | Draggable screen bubble for quick logging over other apps. |
| **Profile Customization** | Set IGN, Union Level, and choose from free bundled avatars. |
| **Home Widget: WIP** | Glance widget for at-a-glance resource and event tracking. |
| **Planners** | Dedicated Pull Planner and To-Do lists for future account planning. |

### In-Depth Feature List
<details>
* **Resource Tracking (Waveplates & Crystals):**
* Accurate passive regeneration math (+1 per 6 minutes for Waveplates, +1 per 12 minutes for Crystals).
* Adheres precisely to in-game limits: Waveplates are soft-capped at 240 (with an ultra-max manual override up to 2400 to reflect overloaded states), and Crystals are strictly hard-capped at 480.
* Dynamic visual status indicators (Depleted, Regenerating, Full, and Overloaded).


* **Astrite & Currency Tracking:**
* Log daily Astrites (maintaining a running total) and visualize them via an interactive 7-day bar chart on the Dashboard.
* Track the custom "Radiant Astrite" currency directly from the profile header.


* **Lunite Pass Management:**
* Toggle Lunite Pass tracking on or off to easily add your daily +90 Astrites.
* Smart AlarmManager scheduling for daily check-in notifications based on Server Time (Manila time/UTC+8 resets).


* **Floating Overlay Bubble:**
* A foreground WindowManager service allows a tracking bubble to hover over other apps.
* Tap the bubble to instantly open a "Log Astrites" quick-add popup without fully opening the WuWaLab app.


* **Profile Integration:**
* Customize your tracking dashboard with your In-Game Name (IGN) and Union Level (UL).
* Choose from high-quality, built-in avatars (Default, Rover, or Beacon).


* **Glance Widget & Background Sync: WIP**
* Monitor your Waveplates, Crystals, and live event banners directly from your home screen.
* Periodic WorkManager background refreshes keep your data up to date every 30 minutes.


* **In-App Planners:**
* Two independently collapsible planners ("Pull Planner" and "To Do Planner") to help organize your gameplay goals and future resource spending.

</details>

---

## Support

If you encounter any issues, experience a bug, or have a feature request, please open an issue in this GitHub repository with a detailed description of the problem.

---
