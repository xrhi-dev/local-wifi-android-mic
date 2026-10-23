"""
WiFi Mic - PC Receiver (Raw PCM, no codec needed)
==================================================
Receives raw audio from your Android phone over UDP Wi-Fi and
routes it into VB-Cable so Discord/OBS hear it as a microphone.

INSTALL (one time, in Command Prompt):
    pip install sounddevice numpy

RUN:
    python receiver.py
"""

import socket
import threading
import time
import sys

# ── Settings (must match the Android app) ────────────────────────────────────
UDP_PORT    = 9999
SAMPLE_RATE = 44100   # Hz
CHANNELS    = 1       # Mono
CHUNK_BYTES = 4096    # bytes per UDP packet
# ─────────────────────────────────────────────────────────────────────────────


def check_dependencies():
    missing = []
    try:
        import sounddevice
    except ImportError:
        missing.append("sounddevice")
    try:
        import numpy
    except ImportError:
        missing.append("numpy")
    if missing:
        print(f"Missing packages. Run this in Command Prompt:")
        print(f"  pip install {' '.join(missing)}")
        sys.exit(1)


def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def choose_output_device(sd):
    """Auto-select VB-Cable, or fall back to default speakers."""
    print("\nAudio output devices:")
    print("─" * 50)
    for i, d in enumerate(sd.query_devices()):
        if d["max_output_channels"] > 0:
            tag = "  ← VB-Cable" if "cable" in d["name"].lower() else ""
            print(f"  [{i}] {d['name']}{tag}")
    print("─" * 50)

    for i, d in enumerate(sd.query_devices()):
        if "cable" in d["name"].lower() and d["max_output_channels"] > 0:
            print(f"\n✓ VB-Cable auto-selected [{i}]: {d['name']}")
            print("  In Discord/OBS → set microphone to 'CABLE Output (VB-Audio)'")
            return i

    print("\n  VB-Cable not found → audio will play through your speakers.")
    print("  Download free: https://vb-audio.com/Cable/  then restart this script.")
    return None  # sounddevice uses system default


def main():
    check_dependencies()
    import sounddevice as sd

    device = choose_output_device(sd)

    # UDP socket — listens on all network interfaces
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("0.0.0.0", UDP_PORT))
    sock.settimeout(1.0)

    ip = get_local_ip()
    print(f"""
{'═' * 52}
  WiFi Mic Receiver  —  READY
{'═' * 52}
  Enter these in the Android app:
    IP Address : {ip}
    Port       : {UDP_PORT}

  Waiting for connection...  (Ctrl+C to stop)
{'═' * 52}
""")

    # Audio output stream — raw 16-bit PCM
    stream = sd.RawOutputStream(
        samplerate=SAMPLE_RATE,
        channels=CHANNELS,
        dtype="int16",
        device=device,
        blocksize=CHUNK_BYTES // 2,  # blocksize is in samples, not bytes
    )
    stream.start()

    # Stats
    pkts = 0
    t0 = time.time()
    first_packet = True

    def print_stats():
        while True:
            time.sleep(5)
            elapsed = time.time() - t0 or 1
            kb = (pkts * CHUNK_BYTES) / 1024
            print(f"  ▶ {pkts} packets  |  {kb:.0f} KB received  |  "
                  f"{kb/elapsed:.1f} KB/s")

    threading.Thread(target=print_stats, daemon=True).start()

    try:
        while True:
            try:
                data, addr = sock.recvfrom(CHUNK_BYTES + 4)

                if first_packet:
                    print(f"  ✓ Connected! Streaming from {addr[0]}")
                    first_packet = False

                # Strip 4-byte sequence number header, write PCM to speaker
                pcm = data[4:] if len(data) > 4 else data
                stream.write(pcm)
                pkts += 1

            except socket.timeout:
                continue  # normal, just loop
            except Exception as e:
                print(f"  Error: {e}")

    except KeyboardInterrupt:
        print("\n  Stopped.")
    finally:
        stream.stop()
        stream.close()
        sock.close()


if __name__ == "__main__":
    main()
