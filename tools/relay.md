# Relay — TCP-прокси для удалённого ADB

Если облачная сессия не может напрямую подключиться к ТВ, запустите relay
на компьютере, который видит ТВ в сети:

```bash
python3 tools/relay.py 192.168.1.100
# Слушает на 0.0.0.0:15555, пробрасывает на 192.168.1.100:5555
```

В облачной сессии:
```bash
export TV_HOST=127.0.0.1
export TV_PORT=15555
python3 tools/tv.py --screenshot shot.png
```

Если relay на другом хосте (Tailscale, VPN):
```bash
export TV_HOST=<tailscale_ip_компьютера>
export TV_PORT=15555
```
