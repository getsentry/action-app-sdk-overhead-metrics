from __future__ import annotations

import abc

from sentry.integrations.client import ApiClient
from sentry.integrations.coding_agent.models import CodingAgentLaunchRequest
from sentry.seer.autofix.utils import CodingAgentState


class CodingAgentClient(ApiClient, abc.ABC):
    """Abstract base API client for coding agents."""
    base_url: str

    @abc.abstractmethod
    def launch(self, *, webhook_url: str, request: CodingAgentLaunchRequest) -> CodingAgentState:
        pass
