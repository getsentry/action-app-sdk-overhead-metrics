from __future__ import annotations
from datetime import datetime
from enum import Enum
from pydantic import BaseModel


class CodingAgentStatus(str, Enum):
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"


class CodingAgentProviderType(str, Enum):
    GITHUB_COPILOT_AGENT = "github_copilot_agent"


class CodingAgentState(BaseModel):
    id: str
    status: CodingAgentStatus
    provider: CodingAgentProviderType
    name: str
    started_at: datetime
    agent_url: str | None = None
