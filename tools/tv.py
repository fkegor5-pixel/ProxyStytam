#!/usr/bin/env python3
"""ADB wrapper: run commands, inject keys, pull screenshots from an Android TV.

Usage:
    tv.py [--host HOST] [--port PORT] [--screenshot PATH] [KEY ...]

Examples:
    tv.py --screenshot shots/home.png              # just screenshot
    tv.py DPAD_RIGHT                                # press key
    tv.py --screenshot shots/after.png DPAD_DOWN    # press key then screenshot
    tv.py 'pm list packages'                        # run shell command
    tv.py 'getprop ro.build.version.sdk'            # query property

Environment:
    TV_HOST   override --host (default: from --host or 192.168.1.1)
    TV_PORT   override --port (default: 5555)
"""
import argparse
import os
import subprocess
import sys
import time

def adb(*args, host=None, port=None, check=True, capture=True):
    addr = f"{host}:{port}"
    cmd = ["adb", "-s", addr] + list(args)
    r = subprocess.run(cmd, capture_output=capture, text=True, timeout=30)
    if check and r.returncode != 0:
        err = r.stderr.strip() if capture else ""
        print(f"adb error: {err}", file=sys.stderr)
        sys.exit(r.returncode)
    return r

def connect(host, port):
    r = subprocess.run(["adb", "connect", f"{host}:{port}"],
                       capture_output=True, text=True, timeout=15)
    out = r.stdout.strip()
    if "connected" in out.lower() or "already" in out.lower():
        return True
    print(f"adb connect failed: {out}", file=sys.stderr)
    return False

def inject_key(key, host, port):
    adb("shell", f"input keyevent {key}", host=host, port=port)

def screenshot(path, host, port):
    remote = "/sdcard/screenshot_tmp.png"
    adb("shell", f"screencap -p {remote}", host=host, port=port)
    # Check file size — 0 bytes means protected content
    r = adb("shell", f"stat -c %s {remote}", host=host, port=port)
    size = int(r.stdout.strip()) if r.stdout.strip().isdigit() else 0
    if size == 0:
        print("Screenshot is 0 bytes (protected content — Live TV, HDMI, or Netflix).",
              file=sys.stderr)
        adb("shell", f"rm -f {remote}", host=host, port=port)
        sys.exit(1)
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    adb("pull", remote, path, host=host, port=port)
    adb("shell", f"rm -f {remote}", host=host, port=port)
    print(f"Screenshot saved: {path} ({size} bytes)")

def shell_cmd(cmd, host, port):
    r = adb("shell", cmd, host=host, port=port, check=False)
    if r.stdout:
        print(r.stdout, end="")
    if r.stderr:
        print(r.stderr, end="", file=sys.stderr)
    return r.returncode

def main():
    p = argparse.ArgumentParser(description="Android TV ADB helper")
    p.add_argument("--host", default=os.environ.get("TV_HOST", "192.168.1.1"))
    p.add_argument("--port", type=int, default=int(os.environ.get("TV_PORT", "5555")))
    p.add_argument("--screenshot", "-s", metavar="PATH", help="Pull screenshot after keys")
    p.add_argument("args", nargs="*", help="Key names (DPAD_UP, HOME, ...) or 'shell command'")
    args = p.parse_args()

    if not connect(args.host, args.port):
        sys.exit(1)

    KEYS = {"DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "DPAD_CENTER",
            "ENTER", "BACK", "HOME", "MENU", "POWER", "VOLUME_UP", "VOLUME_DOWN",
            "MUTE", "TV_INPUT", "CHANNEL_UP", "CHANNEL_DOWN", "MEDIA_PLAY_PAUSE",
            "MEDIA_STOP", "MEDIA_NEXT", "MEDIA_PREVIOUS", "MEDIA_REWIND",
            "MEDIA_FAST_FORWARD", "INFO", "GUIDE", "TV", "SETTINGS",
            "APP_SWITCH", "BOOKMARK", "SEARCH"}

    for a in args.args:
        if a.upper() in KEYS or a.upper().startswith("KEYCODE_"):
            inject_key(a.upper(), args.host, args.port)
            time.sleep(0.3)
        else:
            # treat as shell command
            rc = shell_cmd(a, args.host, args.port)
            if rc != 0:
                sys.exit(rc)

    if args.screenshot:
        time.sleep(0.5)
        screenshot(args.screenshot, args.host, args.port)

if __name__ == "__main__":
    main()
