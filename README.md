# WiFi Mic — Setup Guide [⚠️WiP]

⚠️ WORK IN PROGRESS!

Stream your Android phone's microphone to your PC over Wi-Fi using OPUS audio encoding. 

##NOTE
Designed for QUALITY instead of low lantency. 

---

## How it works

```
Phone mic → OPUS encode → UDP Wi-Fi → PC receive → OPUS decode → VB-Cable → Discord/OBS
```

---

## Part 1 — PC Setup (do this first)

### Step 1: Install VB-Cable (free virtual audio driver)

1. Go to https://vb-audio.com/Cable/
2. Download **VBCABLE_Driver_Pack43.zip**
3. Extract it, right-click **VBCABLE_Setup_x64.exe** → **Run as administrator**
4. Restart your PC when prompted

### Step 2: Install Python (if not already installed)

1. Go to https://python.org/downloads/
2. Download **Python 3.11+**
3. During install, tick **"Add Python to PATH"** ← important!

### Step 3: Install Python dependencies

Open **Command Prompt** (search for `cmd`) and run:

```
pip install pyaudio opuslib
```

If `pyaudio` fails on Windows, try:
```
pip install pipwin
pipwin install pyaudio
```

### Step 4: Run the receiver

```
cd pc-receiver
python receiver.py
```

You'll see something like:
```
=======================================================
  WiFi Mic Receiver RUNNING
=======================================================
  Enter this IP in the Android app: 192.168.1.105
  Port: 9999
  Waiting for audio...
=======================================================
```

**Write down that IP address** — you'll need it on your phone.

### Step 5: Configure your apps

In **Discord**: Settings → Voice & Video → Input Device → **CABLE Output (VB-Audio Virtual Cable)**

In **OBS**: Audio → Mic/Aux → **CABLE Output (VB-Audio Virtual Cable)**

---

## Part 2 — Android App Setup

### Step 1: Install Android Studio

1. Download from https://developer.android.com/studio
2. Install it (accept all defaults)
3. Open Android Studio

### Step 2: Open the project

1. Click **"Open"**
2. Navigate to the `android-app` folder
3. Click **OK**
4. Wait for Gradle to sync (the progress bar at the bottom)

### Step 3: Enable Developer Mode on your phone

1. Go to **Settings → About phone**
2. Tap **Build number** 7 times
3. Go back to **Settings → Developer options**
4. Turn on **USB debugging**

### Step 4: Build and install

1. Connect your phone via USB
2. In Android Studio, click the **▶ Run** button (top toolbar)
3. Select your phone and click **OK**
4. The app installs and opens automatically

---

## Part 3 — Using the App

1. Make sure your phone and PC are on the **same Wi-Fi network**
2. On the PC, run `python receiver.py` and note the IP shown
3. Open the **WiFi Mic** app on your phone
4. Enter the PC's IP address (e.g. `192.168.1.105`)
5. Leave port as `9999`
6. Tap **Start Streaming**
7. Grant microphone permission when asked
8. The PC will show: `✓ Connected! Receiving from 192.168.x.x`

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| "No module named opuslib" | Run `pip install opuslib` |
| "No module named pyaudio" | Run `pipwin install pyaudio` |
| App connects but no audio in Discord | Set Discord mic to "CABLE Output", not "CABLE Input" |
| "Failed to open microphone" | Grant mic permission in Android Settings → Apps → WiFi Mic |
| PC doesn't receive packets | Check phone and PC are on same Wi-Fi; check Windows Firewall allows Python |
| High latency | Lower `BUFFER_FRAMES` in `receiver.py` from 4 to 2 |

### Windows Firewall fix

If packets aren't arriving, Windows Firewall may be blocking Python:

1. Search for **Windows Defender Firewall**
2. Click **Allow an app through firewall**
3. Find **Python** and check both Private and Public
4. Or: when Windows asks "Allow Python through firewall?" click **Allow**

---

## Technical Details

| Setting | Value |
|---------|-------|
| Sample rate | 48,000 Hz |
| Channels | Mono |
| Frame duration | 20 ms |
| OPUS application mode | VOIP (voice optimised) |
| Transport | UDP (low latency) |
| Packet format | 4-byte sequence number + OPUS payload |
| Default port | 9999 |

---

## File Structure

```
wifimic/
├── pc-receiver/
│   ├── receiver.py       ← Run this on your PC
│   └── requirements.txt
└── android-app/
    ├── app/src/main/
    │   ├── java/com/wifimic/
    │   │   └── MainActivity.kt   ← All Android logic
    │   ├── res/layout/
    │   │   └── activity_main.xml ← UI
    │   └── AndroidManifest.xml
    └── build.gradle
```
