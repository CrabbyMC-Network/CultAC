# Proxy Geyser transport

This CrabbyMC fork adds a Geyser extension and a matching CultAC backend transport.
The extension targets Geyser 2.11.3 at commit `751d2aab177f614b9e99c9062959e4f2cb08a5c3`.
Geyser internals are not a stable extension API: another Geyser build needs compatibility
validation before upgrading.

## Protocol and ordering

The backend sends a signed challenge for the exact player UUID and connection nonce.
The gateway captures original Bedrock input before Geyser translates it. CultAC runs
its existing Bedrock simulation, returns a verdict, and the gateway projects only
the accepted movement. A bounded ownership queue preserves projection before the
next input. Reconnection and backend login invalidate the old lease and queued work.

Client-visible native state has a receipt boundary. Bedrock `NetworkStackLatency`
responses establish consumption of metadata, attributes, effects and teleports.
Geyser-generated Java pongs and teleport acknowledgements are not native receipt
proof. The transport uses the normal Bedrock engine and teleport gate; it does not
grant an anticheat exemption or increase movement tolerance.

Control envelopes use HMAC-SHA256 with a 32-byte deployment key, direction, UUID,
connection nonce and ordered sequence. Malformed, forged and stale messages fail
closed. Bounds apply to frames, actions, queues and pending receipts. Keys are
deployment secrets and must never be committed or distributed with release jars.

## Installation contract

- Backend key: `plugins/CultAC/proxy-bridge.key`, base64 encoded, exactly 32 decoded bytes.
- Gateway key: the extension's data folder, `proxy-bridge.key`, with the same trusted
  deployment key. Limit key-file access to the server account.
- Backend: the matching modified CultAC jar on each server using this transport.
- Proxy: the matching extension jar in Geyser's `extensions` directory.

Start both components through normal server restarts. Geyser extensions are not
installed through a Bukkit plugin reload. Backends without an authenticated CultAC
challenge retain their original Geyser behavior.

The extension jar is built for Geyser-Velocity: its fastutil references are relocated
to the copy Geyser-Velocity shades. Other Geyser platforms need their own build.

Restart the proxy before the backends. A backend holding a key accepts only native
Bedrock receipts, so its Bedrock players stall until the proxy runs the extension.
The reverse order is safe because the extension stays unbound until a challenge arrives.

## Validation status

Unit, offline replay and bytecode linkage checks against the deployed server and
Geyser-Velocity jars pass. Live Bedrock-client validation is still required: do not
treat unit tests or linkage checks as proof of live movement compatibility.
