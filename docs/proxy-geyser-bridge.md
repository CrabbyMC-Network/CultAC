# Proxy Geyser transport

This CrabbyMC fork adds a Geyser extension and a matching CultAC backend transport.
The extension is validated against Geyser-Velocity 2.11.3 (b1249, git `f66329d`).
Geyser internals are not a stable extension API: another Geyser build needs compatibility
validation before upgrading.

## Protocol and ordering

The backend sends a signed challenge for the exact player UUID and connection nonce
once the client has acknowledged the backend's spawn teleport. During a Velocity
server switch, backend plugin messages can reach the client before the new login, so
an earlier challenge would be reset by that login.

The gateway captures original Bedrock input before Geyser translates it. CultAC runs
its existing Bedrock simulation, returns a verdict, and the gateway projects only
the accepted movement. A bounded ownership queue preserves projection before the
next input. Reconnection and backend login invalidate the old lease and queued work.

Client-visible native state has a receipt boundary. Bedrock `NetworkStackLatency`
responses establish consumption of metadata, attributes, effects and teleports.
Clients echo these with `fromServer=false`, and devices differ in how they scale the
echoed timestamp, so the gateway matches its own pending markers in every known scale
and enforces their order. Geyser-generated Java pongs and teleport acknowledgements are
not native receipt proof. The transport uses the normal Bedrock engine and teleport gate;
it does not grant an anticheat exemption or increase movement tolerance.

Native writes that reach the client together share one boundary: the gateway sends
the run of state-carrying writes as one `ACTOR_CONTEXT_BATCH`, writes the packets in
their original order at the backend's boundary and follows them with one receipt. The
backend applies the states in order at that boundary, as the client processes them in
the same flush. Self teleports and block-update boundaries keep their own boundary.
This keeps bridge traffic well below the backends' packet-rate limits in busy areas.

Gateway messages are sequenced under one lock and written in order on the downstream
event loop. Authenticated bridge messages are excluded from ViaVersion's packet
limiter, like CultAC's own transaction responses.

Control envelopes use HMAC-SHA256 with a 32-byte deployment key, direction, UUID,
connection nonce and ordered sequence. Malformed, forged and stale messages fail
closed. Bounds apply to frames, actions, queues and pending receipts. Keys are
deployment secrets and must never be committed or distributed with release jars.

The envelope carries a protocol version (currently 2). A gateway and backend on
different versions refuse each other on the first message.

## Installation contract

- Backend key: `plugins/CultAC/proxy-bridge.key`, base64 encoded, exactly 32 decoded bytes.
- Gateway key: the extension's data folder, `proxy-bridge.key`, with the same trusted
  deployment key. Limit key-file access to the server account.
- Backend: the matching modified CultAC jar on each server using this transport.
- Proxy: the matching extension jar in Geyser's `extensions` directory.
- Deploy the gateway and backend builds as a pair; they must share the protocol version.

Start both components through normal server restarts. Geyser extensions are not
installed through a Bukkit plugin reload. Backends without an authenticated CultAC
challenge retain their original Geyser behavior.

The extension jar is built for Geyser-Velocity: its fastutil references are relocated
to the copy Geyser-Velocity shades. Other Geyser platforms need their own build.

The backend bundles Cloudburst protocol and math classes. The bundled math build
resolves its implementations through the thread context class loader, so the bridge
initialises them with the plugin's own loader at registration.

Restart the proxy before the backends. A backend holding a key accepts only native
Bedrock receipts, so its Bedrock players stall until the proxy runs the extension.
The reverse order is safe because the extension stays unbound until a challenge arrives.

## Diagnostics

When the gateway drops a session it logs `[cultacproxybridge] <reason> for <player>:`
with the exception and its CultAC stack origin. The first initial-state capture
failure is logged with the native packet type. The backend logs
`Proxy Bedrock bridge rejected a connection:` with the exception.

## Validation status

Unit, offline replay, bytecode linkage, class-loading and concurrency checks against
the deployed server and Geyser-Velocity jars pass. Live Bedrock-client validation is
still required: do not treat these as proof of live movement compatibility.
