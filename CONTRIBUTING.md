# Contributing to WuWaLab

First off — thanks for even considering this. WuWaLab started as a one-person passion project for the Wuthering Waves community, and any help, however small, is genuinely appreciated.

You don't need to be a professional Android developer to contribute. Reporting a bug, suggesting a feature, fixing a typo, or improving the docs all count as real contributions.

## Ways to Contribute

You don't have to write code to help out:

- **Report a bug** — something crashed, a number looks wrong, a widget isn't updating.
- **Suggest a feature** — something you wish WuWaLab could do.
- **Improve the docs** — clarify something confusing in the README or this file.
- **Submit a fix or feature** — via a pull request, if you're comfortable with Kotlin/Android.

## Reporting a Bug

Open an [issue](../../issues) and include:

1. **What happened** vs **what you expected to happen**.
2. **Steps to reproduce it** — as exact as you can get.
3. Your **Android version** and, if relevant, your **device model**.
4. A screenshot or screen recording, if it's a visual bug.
5. Whether it happens every time, or only sometimes.

The more specific you are, the faster it gets fixed. "The widget is broken" is hard to act on; "the Waveplate widget stopped updating after I force-closed the app on Android 13" is easy to act on.

## Suggesting a Feature

Open an [issue](../../issues) and describe:

- What you want to be able to do.
- Why it'd be useful (what problem it solves for you).
- Any examples of how it might look or work, if you have ideas.

Not every suggestion will get built — some might not fit the app's scope, and that's okay — but all of them get read.

## Contributing Code

If you want to fix something or build a feature yourself:

### 1. Set up the project

- **Requirements:** Android Studio (recent stable version), JDK 17+.
- Clone the repo and open it in Android Studio — it should sync and build out of the box with no extra setup.
- Minimum SDK is 30 (Android 11), target SDK is 37.

### 2. Before you start coding

For anything beyond a tiny fix (typo, small bug), it's worth opening an issue first to say what you're planning. This avoids two people accidentally working on the same thing, and lets us confirm the approach makes sense before you sink time into it.

### 3. Making changes

- Keep pull requests focused — one fix or one feature per PR is much easier to review than a bundle of unrelated changes.
- Follow the existing code style you see in the file you're editing (naming, formatting, how repositories/screens are structured). Consistency matters more than personal preference here.
- If you're adding a new screen, repository, or widget, look at how similar existing ones are structured (e.g. an existing `*Repository.kt` or `*Screen.kt`) and follow the same pattern.
- Test your change on a real device or emulator before opening the PR — especially anything touching widgets, notifications, or background work, since those are easy to break silently.

### 4. Submitting a pull request

1. Fork the repo and create a branch with a descriptive name (e.g. `fix/waveplate-widget-crash`).
2. Commit your changes with clear, specific commit messages.
3. Open a PR against `main` and describe:
   - What the change does.
   - Why it's needed.
   - How you tested it.
4. Be open to feedback — review comments are about the code, not you, and a few rounds of back-and-forth is normal.

## What Not to Do

- Please don't submit AI-generated code changes you haven't personally read, tested, and understood — it slows review down for everyone.
- Please don't include unrelated formatting/reformatting changes mixed into a functional PR — it makes the actual change hard to see in the diff.
- Please don't add analytics, ads, or telemetry of any kind. WuWaLab is intentionally ad-free and doesn't track users — that's not up for negotiation in contributions.

## Code of Conduct

Be respectful. Disagreements about implementation are fine and normal; personal attacks, harassment, or bad-faith arguing are not. Keep it about the code and the app.

## Questions?

If anything here is unclear, or you're not sure whether something is worth an issue or a PR, just ask — open an issue with your question, or drop by the [Discord](https://discord.gg/renjxYBEZM).

Thanks again for helping make WuWaLab better.
