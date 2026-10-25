#!/usr/bin/env python3
"""Check that ADB port is closed on the TV (port 5555 refuses connections).

Usage: adb-closed.py <tv_ip> [port]
Exit 0 = port refused (good), exit 1 = port open or unreachable.
"""
import socket
import sys

def main():
    host = sys.argv[1] if len(sys.argv) > 1 else "192.168.1.1"
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 5555

    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(5)
    try:
        s.connect((host, port))
        s.close()
        print(f"OPEN — port {port} on {host} is still accepting connections!")
        print("ADB is still enabled. Turn off USB debugging on the TV.")
        return 1
    except ConnectionRefusedError:
        print(f"CLOSED — port {port} on {host} refused the connection. ADB is off.")
        return 0
    except socket.timeout:
        print(f"TIMEOUT — port {port} on {host} did not respond.")
        print("Could be firewall or TV is off. Cannot confirm ADB is closed.")
        return 1
    except OSError as e:
        print(f"ERROR — {e}")
        print("Cannot confirm ADB state. If on macOS, Local Network privacy may block this.")
        return 1

if __name__ == "__main__":
    sys.exit(main())
