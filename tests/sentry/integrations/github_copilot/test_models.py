import warnings

import pytest
from pydantic import ValidationError

warnings.filterwarnings("ignore", category=DeprecationWarning, module="pydantic")

from sentry.integrations.github_copilot.models import (
    GithubCopilotTask,
    GithubCopilotTaskResponse,
)


class TestGithubCopilotTaskResponse:
    def test_wrapped_response_format(self) -> None:
        """Test that the wrapped {"task": {...}} format still works"""
        data = {
            "task": {
                "id": "task-123",
                "state": "completed",
                "created_at": "2024-01-01T00:00:00Z",
            }
        }
        response = GithubCopilotTaskResponse.validate(data)
        assert isinstance(response.task, GithubCopilotTask)
        assert response.task.id == "task-123"
        assert response.task.state == "completed"

    def test_unwrapped_response_format(self) -> None:
        """Test that the unwrapped (direct task object) format works"""
        data = {
            "id": "task-456",
            "state": "in_progress",
            "created_at": "2024-06-01T12:00:00Z",
        }
        response = GithubCopilotTaskResponse.validate(data)
        assert isinstance(response.task, GithubCopilotTask)
        assert response.task.id == "task-456"
        assert response.task.state == "in_progress"

    def test_wrapped_format_with_full_task(self) -> None:
        """Test wrapped format with all task fields"""
        data = {
            "task": {
                "id": "task-789",
                "state": "completed",
                "status": "done",
                "artifacts": [
                    {
                        "provider": "github",
                        "type": "github_resource",
                        "data": {"id": 456, "type": "pull", "global_id": "PR_abc"},
                    }
                ],
                "created_at": "2024-01-01T00:00:00Z",
            }
        }
        response = GithubCopilotTaskResponse.validate(data)
        assert response.task.id == "task-789"
        assert response.task.artifacts is not None
        assert len(response.task.artifacts) == 1
        assert response.task.artifacts[0].data.global_id == "PR_abc"

    def test_unwrapped_format_with_full_task(self) -> None:
        """Test unwrapped format with all task fields"""
        data = {
            "id": "task-789",
            "state": "completed",
            "status": "done",
            "artifacts": [
                {
                    "provider": "github",
                    "type": "github_resource",
                    "data": {"id": 456, "type": "pull", "global_id": "PR_abc"},
                }
            ],
            "created_at": "2024-01-01T00:00:00Z",
        }
        response = GithubCopilotTaskResponse.validate(data)
        assert response.task.id == "task-789"
        assert response.task.artifacts is not None
        assert len(response.task.artifacts) == 1
        assert response.task.artifacts[0].data.global_id == "PR_abc"

    def test_unwrapped_minimal_response(self) -> None:
        """Test unwrapped format with only required fields"""
        data = {"id": "task-minimal"}
        response = GithubCopilotTaskResponse.validate(data)
        assert response.task.id == "task-minimal"
        assert response.task.state is None
        assert response.task.artifacts is None

    def test_invalid_response_no_id(self) -> None:
        """Test that a response without 'task' or 'id' fails validation"""
        data = {"state": "completed"}
        with pytest.raises(ValidationError):
            GithubCopilotTaskResponse.validate(data)

    def test_wrapped_format_prioritized(self) -> None:
        """Test that wrapped format is prioritized when 'task' key exists as a dict"""
        data = {
            "task": {
                "id": "wrapped-id",
                "state": "completed",
            },
            "id": "top-level-id",
        }
        response = GithubCopilotTaskResponse.validate(data)
        assert response.task.id == "wrapped-id"
