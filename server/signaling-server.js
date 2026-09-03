#!/usr/bin/env node
/**
 * Port-SIP reference signaling server.
 *
 * A tiny WebSocket relay that lets two WebRTC clients exchange SDP offers/answers
 * and ICE candidates. It maintains a registry of online users (registered by
 * username) and routes messages to the target user, tagging each relayed message
 * with the sender (`from`) so the receiver knows who to answer.
 *
 * Protocol (JSON frames, see app/src/main/java/com/chatapp/modern/webrtc/Signal.kt):
 *
 *   {"type":"register","user":"alice"}              client -> server
 *   {"type":"offer","to":"bob",  "sdp":"..."}        client -> server (routed to bob)
 *   {"type":"answer","to":"alice","sdp":"..."}       client -> server (routed to alice)
 *   {"type":"candidate","to":"bob","mid":"0","index":0,"sdp":"..."}
 *   {"type":"bye","to":"bob"}
 *   {"type":"dtmf","to":"bob","digit":"5"}
 *
 * Relay becomes {"type":"offer","from":"alice","sdp":"..."} with an optional
 * `to` field pointing back at the relay target.
 *
 * Run:   npm install   &&   npm start
 * Then point the Android app's "Signaling server" setting to
 * `ws://<your-ip>:8080` (use `wss://` behind TLS in production).
 */

import { createServer } from 'node:http';
import { WebSocketServer } from 'ws';

const PORT = process.env.PORT || 8080;

// Map username -> ws
const usersByUsername = new Map();
// Map ws -> username
const usernameBySocket = new Map();

const httpServer = createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('Port-SIP signaling server is running.\n');
});

const wss = new WebSocketServer({ server: httpServer });

function send(socket, obj) {
  if (socket && socket.readyState === socket.OPEN) {
    socket.send(JSON.stringify(obj));
  }
}

wss.on('connection', (socket) => {
  console.log('[+] client connected');

  socket.on('message', (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch {
      return; // ignore malformed frames
    }

    const type = msg.type;

    if (type === 'register') {
      const user = String(msg.user || '').trim();
      if (!user) {
        send(socket, { type: 'error', reason: 'register requires a user' });
        return;
      }
      // If the user was already connected elsewhere, evict the old socket.
      const existing = usersByUsername.get(user);
      if (existing && existing !== socket) {
        existing.close(4000, 'replaced by new connection');
      }
      usersByUsername.set(user, socket);
      usernameBySocket.set(socket, user);
      console.log(`[*] registered user "${user}"`);
      return;
    }

    // All other message types require a target recipient.
    const from = usernameBySocket.get(socket);
    if (!from) {
      send(socket, { type: 'error', reason: 'not registered' });
      return;
    }

    const to = String(msg.to || '').trim();
    if (!to) {
      send(socket, { type: 'error', reason: 'message requires a "to" target' });
      return;
    }

    const target = usersByUsername.get(to);
    if (!target) {
      // The peer is offline; inform the sender.
      send(socket, { type: 'error', reason: `user "${to}" is not online` });
      return;
    }

    // Relay, tagging with the sender. Keep `to` for clarity.
    const relayed = { ...msg, from };
    delete relayed.to;
    send(target, relayed);
  });

  socket.on('close', () => {
    const user = usernameBySocket.get(socket);
    if (user) {
      if (usersByUsername.get(user) === socket) {
        usersByUsername.delete(user);
        console.log(`[-] user "${user}" disconnected`);
      }
      usernameBySocket.delete(socket);
    }
  });

  socket.on('error', (err) => {
    console.error('[!] socket error:', err.message);
  });
});

httpServer.listen(PORT, () => {
  console.log(`Port-SIP signaling server listening on http://0.0.0.0:${PORT}`);
});
