"""
LiveKit Agents AvatarSession connector for the Modal MuseTalk 1.5 Avatar Worker.

Usage inside any LiveKit Agent entrypoint:
    avatar = MuseTalkAvatarSession(avatar_id="elena")
    await avatar.start(session, room=ctx.room)
    await session.start(
        agent=agent,
        room=ctx.room,
        room_options=room_io.RoomOptions(audio_input=..., audio_output=False),
    )
"""

from __future__ import annotations

import logging
import os
import aiohttp
from livekit import api, rtc
from livekit.agents import AgentSession
from livekit.agents.voice.avatar import DataStreamAudioOutput

logger = logging.getLogger("avatar-session")

DEFAULT_MODAL_DISPATCHER_URL = (
    "https://dany-boza-cc--musetalk-livekit-avatar-dispatcher.us-east.modal.run"
)


class MuseTalkAvatarSession:
    def __init__(
        self,
        *,
        avatar_id: str = "elena",
        dispatcher_url: str | None = None,
        avatar_identity: str | None = None,
        sample_rate: int = 24000,
    ) -> None:
        self.avatar_id = avatar_id
        self.dispatcher_url = (
            dispatcher_url
            or os.getenv("AVATAR_DISPATCHER_URL")
            or DEFAULT_MODAL_DISPATCHER_URL
        ).rstrip("/")
        self.avatar_identity = avatar_identity or f"avatar-{avatar_id}"
        self.sample_rate = sample_rate
        self.call_id: str | None = None

    async def start(
        self,
        agent_session: AgentSession,
        *,
        room: rtc.Room,
        livekit_url: str | None = None,
        livekit_api_key: str | None = None,
        livekit_api_secret: str | None = None,
    ) -> None:
        lk_url = livekit_url or os.getenv("LIVEKIT_URL")
        lk_key = livekit_api_key or os.getenv("LIVEKIT_API_KEY")
        lk_secret = livekit_api_secret or os.getenv("LIVEKIT_API_SECRET")
        if not lk_url or not lk_key or not lk_secret:
            raise RuntimeError(
                "LIVEKIT_URL, LIVEKIT_API_KEY, and LIVEKIT_API_SECRET are required to launch MuseTalkAvatarSession."
            )

        agent_identity = room.local_participant.identity

        avatar_token = (
            api.AccessToken(lk_key, lk_secret)
            .with_identity(self.avatar_identity)
            .with_name(self.avatar_id.capitalize())
            .with_kind("agent")
            .with_attributes({"lk.publish_on_behalf": agent_identity})
            .with_grants(
                api.VideoGrants(
                    room_join=True,
                    room=room.name,
                    can_publish=True,
                    can_subscribe=True,
                    can_publish_data=True,
                )
            )
            .to_jwt()
        )

        # Spawn the Modal GPU worker into the LiveKit room
        async with aiohttp.ClientSession() as http:
            async with http.post(
                f"{self.dispatcher_url}/start-session",
                json={
                    "livekit_url": lk_url,
                    "avatar_token": avatar_token,
                    "avatar_id": self.avatar_id,
                    "agent_identity": agent_identity,
                },
                timeout=aiohttp.ClientTimeout(total=30),
            ) as resp:
                body = await resp.text()
                if resp.status >= 400:
                    raise RuntimeError(
                        f"Modal avatar dispatcher failed ({resp.status}): {body}"
                    )
                data = await resp.json()
                self.call_id = data.get("call_id")

        logger.info(
            "Spawned Modal MuseTalk avatar '%s' (identity=%s, call_id=%s) for room '%s'",
            self.avatar_id,
            self.avatar_identity,
            self.call_id,
            room.name,
        )

        # Wait until the Modal GPU worker joins and publishes its video track so
        # AgentSession.on_enter() / TTS never times out during a cold GPU boot.
        import asyncio
        import time

        t_wait = time.time()
        ready = False
        while time.time() - t_wait < 90.0:
            if not room.isconnected():
                raise RuntimeError("Room disconnected while waiting for MuseTalk avatar.")
            remote = room.remote_participants.get(self.avatar_identity)
            if remote is not None:
                for pub in remote.track_publications.values():
                    if pub.kind == rtc.TrackKind.KIND_VIDEO:
                        ready = True
                        break
            if ready:
                break
            await asyncio.sleep(0.5)

        if not ready:
            raise TimeoutError(
                f"Timed out waiting for Modal avatar '{self.avatar_identity}' to publish video."
            )

        logger.info(
            "Modal MuseTalk avatar '%s' is live after %.1fs",
            self.avatar_identity,
            time.time() - t_wait,
        )

        # Route all AgentSession TTS PCM audio over LiveKit DataStream to the avatar worker
        agent_session.output.audio = DataStreamAudioOutput(
            room=room,
            destination_identity=self.avatar_identity,
            sample_rate=self.sample_rate,
            wait_remote_track=rtc.TrackKind.KIND_VIDEO,
        )

    async def aclose(self) -> None:
        if not self.call_id:
            return
        try:
            async with aiohttp.ClientSession() as http:
                await http.post(
                    f"{self.dispatcher_url}/stop-session",
                    json={"call_id": self.call_id},
                    timeout=aiohttp.ClientTimeout(total=10),
                )
        except Exception as e:
            logger.debug("Ignoring stop-session cleanup error: %s", e)
        finally:
            self.call_id = None
