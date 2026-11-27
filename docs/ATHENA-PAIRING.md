# Pair Vox with Athena

Design only. This connection requires gateway and Athena changes.

## Current controls

- Vox uses the Hermes gateway for its turns. Its shared bearer key gives access to the gateway. The editable `X-Hermes-Session-Key` header selects a conversation scope.
- Vox already supports an Android KeyChain client certificate for each HTTPS origin. Use the existing certificate setting to identify a paired device. See [Per-origin client certificates](MTLS-PER-ORIGIN.md).
- Athena's `owner_context` policy accepts a local CLI context or a validated owner Discord direct message. The policy checks the session record for that direct message. An API turn has no owner access under this policy.

## Server binding

The certificate identifies the device. It cannot identify the speaker. The owner still needs to decide whether a locked phone may request or read private notes aloud.

1. Give each paired device its own client certificate. The owner enrolls its certificate fingerprint in a private server-side binding to the owner notebook. Keep the gateway URL, certificate material, and bindings in private configuration.
2. Require mutual TLS on the paired gateway. Validate the certificate and check the binding and revocation state on each request, including resumed connections and existing streams. If a proxy terminates TLS, use a private authenticated connection to the gateway and remove any incoming identity headers before adding the verified device identity. Deny direct access to the paired backend.
3. Set the owner notebook scope from the server binding. Extend the Athena owner check to require this verified request context for API access. Apply it to prefetch, recall, note submission, and compatibility hooks. Scope transcript lookup and continuation to sessions the server has bound to that device. The bearer, session header, client session ID, and response ID alone give no owner access.
4. Retrieve published notebook claims through Athena. Keep memory write approval in effect. If Athena puts a note in its inbox, report it as pending verification. On the paired gateway, deny access when authentication or the binding fails. Do not fall back to the owner profile.

## Acceptance checks

- Pair a test device. Start a new turn and resume it. Recall a published test claim from the owner notebook. Submit an approved test note and check that Athena reports its actual state.
- Use a valid gateway bearer with no enrolled certificate. Repeat with a different certificate and the same session header or continuation IDs. Verify that neither request reads or writes owner notebook data or resumes the paired transcript.
- Revoke the test binding during an active stream. End the stream and reject the next request with the same certificate and session IDs. Verify that the device receives no further owner notebook data and that a separate enrolled device still works.
- Once the owner selects a lock-screen policy, lock the test device during playback. Check continued playback and a new spoken request against that policy.
