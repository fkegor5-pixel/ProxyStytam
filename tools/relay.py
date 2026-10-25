#!/usr/bin/env python3
"""TCP relay: forward local port to a remote ADB device.

Run on the machine that can reach the TV:
    python3 relay.py 192.168.1.100
    # Now the cloud session uses TV_HOST=127.0.0.1 TV_PORT=15555

The relay listens on 0.0.0.0:15555 and forwards to <TV_IP>:5555.
"""
import argparse
import selectors
import socket
import sys
import threading

def forward(src, dst):
    try:
        while True:
            data = src.recv(4096)
            if not data:
                break
            dst.sendall(data)
    except (ConnectionResetError, BrokenPipeError, OSError):
        pass
    finally:
        src.close()
        dst.close()

def main():
    p = argparse.ArgumentParser(description="ADB TCP relay")
    p.add_argument("tv_ip", help="TV IP address")
    p.add_argument("--tv-port", type=int, default=5555)
    p.add_argument("--listen-port", type=int, default=15555)
    args = p.parse_args()

    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", args.listen_port))
    srv.listen(5)
    print(f"Relay listening on 0.0.0.0:{args.listen_port} → {args.tv_ip}:{args.tv_port}")

    while True:
        client, addr = srv.accept()
        print(f"Connection from {addr}")
        try:
            remote = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            remote.settimeout(10)
            remote.connect((args.tv_ip, args.tv_port))
            remote.settimeout(None)
        except OSError as e:
            print(f"Cannot reach TV: {e}")
            client.close()
            continue
        threading.Thread(target=forward, args=(client, remote), daemon=True).start()
        threading.Thread(target=forward, args=(remote, client), daemon=True).start()

if __name__ == "__main__":
    main()
