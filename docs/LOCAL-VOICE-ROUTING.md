# Local voice routing

The first stage covers two English commands in an active call. It uses the final transcript from on-device speech recognition. The setting is off by default. This keeps the behavior specified in [upstream decision D38](DECISIONS.md#d38--the-phone-parses-nothing-the-mind-decides-and-presence-may-look-but-never-act). With the setting on, a matching command can use an existing phone action when its permission is Allow. Other turns use the current Hermes path. Pairing with an Athena owner notebook is a separate [server change](ATHENA-PAIRING.md).

## Try the preview

1. Set up the Hermes gateway and on-device speech recognition. Enable Phone tools. Set the timer or volume permission to Allow.
2. In Settings, open Hearing and enable **Local voice commands (preview)**. Start a voice call.
3. Say “Athena, set a timer for five minutes.” Check that one timer appears on the phone. Say “Set the media volume to thirty percent.” Check the reported level against the level Android applied. The name is optional during a call. The phone waits for the final transcript before it acts. A question such as “Athena, did I set a timer?” goes to Hermes.
4. Change the relevant permission to Ask. Repeat the command. Vox sends the request to Hermes through its usual tool approval path. With permission Off, Hermes can also receive the request but cannot use that phone action.

Starting a new call still requires a working gateway. This preview covers the action during a call. To return to the default behavior, turn off the preview in Hearing.

For the device test, interrupt a local confirmation and ask Hermes a new question. Inspect the test gateway request. It must contain only the new turn and eligible Hermes context. In the debug build, use the test harness to submit text while the timer action is in progress. Check that the timer runs once and that each response belongs to the correct request.

## Check Athena settings

Open **Settings > Hearing > Athena diagnostics**. The report shows the app version, the two switch settings, the selected speech recognizer, whether local model files are present, and the current timer and volume permissions. The selected recognizer can differ from the one used during a call if a model fails to load. **Copy report** copies only the displayed summary to the clipboard.

For a memory check, open the owner's private Discord chat with Athena. Select `/memory-dream` and choose `status` or `diagnose` in the `args` field. Submitting the command with an empty field starts a run. The Android report is available offline and does not query memory.

The app version ends in `-athena-draft`. In **Entity & Connection > Teach your agent this app**, the install command uses the fork guide that matches this draft. The owner can inspect the command before deciding whether to install the guide on a gateway.

## Next experiment

Can the phone model recognize more ways to ask for a timer or a volume change? Keep the existing voice response and permission checks in place. Run the model on saved synthetic transcripts first. Give the model three possible results: a timer duration, a media volume level, or a handoff to Hermes. Record its prediction without executing an action or sending a request.

| Synthetic final transcript | Expected result |
| --- | --- |
| Set a timer for five minutes. | Timer, 300 seconds |
| Give me a five-minute timer. | Timer, 300 seconds |
| Set the media volume to thirty percent. | Media volume, 30 percent |
| Put the music at a quarter of full volume. | Media volume, 25 percent |
| Remind me in five minutes. | Handoff |
| Did I set a timer for five minutes? | Handoff |
| Don't set the volume to thirty percent. | Handoff |
| Set the volume to thirty percent and start a timer. | Handoff |
| What volume did I use yesterday? | Handoff |
| Set a timer for five. | Handoff |

Count correct actions with the exact value, missed local actions, and incorrect local actions separately. Compare the model with the command parser on the same inputs. An incorrect local action blocks an execution trial. Repeat on the target phone with spoken inputs, new phrases, and background noise. Record the phone, model version, runtime, and time from final transcript to prediction. This table is a starting set for the experiment.
