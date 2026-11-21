# Privacy

AA Action Buttons is designed to run locally on your phone.

## What the app stores

- Button configuration (labels and action settings) in app **SharedPreferences** on the device.
- An optional on-device **debug log** (also SharedPreferences) that you can view, copy, or clear in the app. That log may include Tasker **task names** and similar labels you configured; it stays on the device unless you copy it elsewhere.

There are **no accounts**, **no analytics/telemetry SDKs**, and **no cloud backend** operated by this project.

## What may leave the device

- **HTTP buttons:** if you configure a URL, the app sends that request to the host you specify. That is entirely under your control and subject to that host’s privacy practices.
- **Intent / broadcast buttons:** may deliver data to other apps you choose on the same device.
- **Tasker buttons:** talk to Tasker on the same device via local Android IPC (broadcasts / content providers). See Tasker’s own privacy policy for that app.

## Android Auto

Android Auto (Google) connects to this app as a media browser client so it can show your button list on the car display. That connection is part of the Android Auto / Google stack on your phone; this project does not send button configs to a separate server.

## Contact

For privacy questions about this project, open an issue on the GitHub repository.
