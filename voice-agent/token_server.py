import json
import os
import uuid
from typing import Optional
from dotenv import load_dotenv
from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from livekit import api

from voices import resolve_cartesia_voice

dotenv_path = os.path.join(os.path.dirname(__file__), ".env")
load_dotenv(dotenv_path)

app = FastAPI(title="LiveKit Token Service")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

LIVEKIT_URL = os.getenv("LIVEKIT_URL", "wss://talkie-nhixgxmo.livekit.cloud")
LIVEKIT_API_KEY = os.getenv("LIVEKIT_API_KEY")
LIVEKIT_API_SECRET = os.getenv("LIVEKIT_API_SECRET")


class TokenRequest(BaseModel):
    room_name: Optional[str] = None
    participant_name: Optional[str] = None
    voice: Optional[str] = None
    tutor_voice: Optional[str] = None
    language: Optional[str] = None


def generate_token(
    room_name: str,
    participant_name: str,
    metadata: Optional[str] = None,
) -> str:
    token_builder = (
        api.AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET)
        .with_identity(participant_name)
        .with_name(participant_name)
        .with_grants(
            api.VideoGrants(
                room_join=True,
                room=room_name,
                can_publish=True,
                can_subscribe=True,
                can_publish_data=True,
            )
        )
    )
    if metadata:
        token_builder = token_builder.with_metadata(metadata)
    return token_builder.to_jwt()


@app.get("/api/health")
def health_check():
    return {
        "status": "ok",
        "livekit_url": LIVEKIT_URL,
    }


@app.get("/api/token")
def get_token(
    room_name: Optional[str] = Query(None),
    participant_name: Optional[str] = Query(None),
):
    room = room_name or f"zuno-{str(uuid.uuid4())[:8]}"
    name = participant_name or f"user-{str(uuid.uuid4())[:4]}"
    token = generate_token(room, name)
    return {
        "token": token,
        "url": LIVEKIT_URL,
        "room": room,
        "identity": name,
    }


@app.post("/api/token")
def create_token(req: TokenRequest):
    room = req.room_name or f"zuno-{str(uuid.uuid4())[:8]}"
    name = req.participant_name or f"user-{str(uuid.uuid4())[:4]}"
    metadata = json.dumps({
        "provider": "cartesia",
        "voice": resolve_cartesia_voice(req.voice, req.tutor_voice),
        "language": req.language or "en",
    })
    token = generate_token(room, name, metadata=metadata)
    return {
        "token": token,
        "url": LIVEKIT_URL,
        "room": room,
        "identity": name,
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
