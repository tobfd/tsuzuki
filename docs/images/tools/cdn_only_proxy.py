# Tiny CONNECT proxy for the emulator: lets only the AniList image CDN through, refuses the API.
import socket, threading, sys
ALLOW = {"s4.anilist.co"}
def pipe(a, b):
    try:
        while True:
            d = a.recv(65536)
            if not d: break
            b.sendall(d)
    except Exception: pass
    finally:
        for s in (a, b):
            try: s.close()
            except Exception: pass
def handle(c):
    try:
        req = b""
        while b"\r\n\r\n" not in req:
            d = c.recv(4096)
            if not d: c.close(); return
            req += d
        line = req.split(b"\r\n")[0].decode()
        method, target = line.split()[0], line.split()[1]
        host = target.split(":")[0] if method == "CONNECT" else target.split("/")[2].split(":")[0]
        if method != "CONNECT" or host not in ALLOW:
            print("DENY", line, flush=True)
            c.sendall(b"HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n"); c.close(); return
        print("ALLOW", line, flush=True)
        h, p = target.split(":")
        u = socket.create_connection((h, int(p)))
        c.sendall(b"HTTP/1.1 200 Connection established\r\n\r\n")
        threading.Thread(target=pipe, args=(c, u), daemon=True).start()
        pipe(u, c)
    except Exception as e:
        print("ERR", e, flush=True)
        try: c.close()
        except Exception: pass
s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("127.0.0.1", 8888)); s.listen(50)
print("listening", flush=True)
while True:
    c, _ = s.accept()
    threading.Thread(target=handle, args=(c,), daemon=True).start()
