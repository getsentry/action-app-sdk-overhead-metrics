"""
Tests for the fix to handle stale webhook tasks after SentryAppInstallation deletion.

When a SentryAppInstallation is deleted, its ServiceHook is cascade-deleted via FK.
Stale task messages queued before the deletion may still attempt webhook delivery.
The fix ensures that send_webhooks() gracefully halts when:
  1. The ServiceHook is not found (cascade-deleted)
  2. The installation itself has been deleted

Instead of raising SentryAppSentryError (which gets recorded as a failure),
it records a halt with MISSING_INSTALLATION reason and returns early.
"""

from unittest.mock import MagicMock, patch

import pytest


class TestSendWebhooksDeletedInstallation:
    """Test that send_webhooks handles deleted installations gracefully."""

    @patch("sentry.sentry_apps.tasks.sentry_apps.send_and_save_webhook_request")
    @patch("sentry.sentry_apps.tasks.sentry_apps.app_service")
    @patch("sentry.sentry_apps.tasks.sentry_apps._load_service_hook")
    @patch("sentry.sentry_apps.tasks.sentry_apps.SentryAppInteractionEvent")
    def test_send_webhooks_returns_early_when_installation_deleted(
        self,
        mock_interaction_event,
        mock_load_service_hook,
        mock_app_service,
        mock_send_request,
    ):
        """
        When ServiceHook is missing because the installation was deleted,
        send_webhooks should gracefully halt instead of raising an error.
        """
        # Setup mocks
        mock_lifecycle = MagicMock()
        mock_context = MagicMock()
        mock_context.__enter__ = MagicMock(return_value=mock_lifecycle)
        mock_context.__exit__ = MagicMock(return_value=False)
        mock_interaction_event.return_value.capture.return_value = mock_context

        # ServiceHook not found (cascade-deleted)
        mock_load_service_hook.return_value = None

        # Installation no longer exists (deleted)
        mock_app_service.installation_by_id.return_value = None

        # Create a mock installation
        mock_installation = MagicMock()
        mock_installation.id = 123
        mock_installation.organization_id = 456
        mock_installation.sentry_app.events = ["issue.resolved"]

        from sentry.sentry_apps.tasks.sentry_apps import send_webhooks

        # Should NOT raise SentryAppSentryError
        send_webhooks(
            installation=mock_installation,
            event="issue.resolved",
            data={"issue": {}},
        )

        # Should have called record_halt, not raised an error
        mock_lifecycle.record_halt.assert_called_once()

        # Should NOT have attempted to send the webhook
        mock_send_request.assert_not_called()

    @patch("sentry.sentry_apps.tasks.sentry_apps.send_and_save_webhook_request")
    @patch("sentry.sentry_apps.tasks.sentry_apps.app_service")
    @patch("sentry.sentry_apps.tasks.sentry_apps._load_service_hook")
    @patch("sentry.sentry_apps.tasks.sentry_apps.SentryAppInteractionEvent")
    def test_send_webhooks_raises_error_when_servicehook_missing_but_installation_exists(
        self,
        mock_interaction_event,
        mock_load_service_hook,
        mock_app_service,
        mock_send_request,
    ):
        """
        When ServiceHook is missing but the installation still exists,
        send_webhooks should raise SentryAppSentryError as before (genuine error).
        """
        from sentry.sentry_apps.utils.errors import SentryAppSentryError

        # Setup mocks
        mock_lifecycle = MagicMock()
        mock_context = MagicMock()
        mock_context.__enter__ = MagicMock(return_value=mock_lifecycle)
        mock_context.__exit__ = MagicMock(return_value=False)
        mock_interaction_event.return_value.capture.return_value = mock_context

        # ServiceHook not found
        mock_load_service_hook.return_value = None

        # Installation still exists
        mock_app_service.installation_by_id.return_value = MagicMock()

        # Create a mock installation
        mock_installation = MagicMock()
        mock_installation.id = 123
        mock_installation.organization_id = 456
        mock_installation.uuid = "test-uuid"
        mock_installation.sentry_app.id = 789
        mock_installation.sentry_app.events = ["issue.resolved"]
        mock_installation.sentry_app.webhook_url = "https://example.com/webhook"

        from sentry.sentry_apps.tasks.sentry_apps import send_webhooks

        # Should raise SentryAppSentryError because servicehook is genuinely missing
        with pytest.raises(SentryAppSentryError):
            send_webhooks(
                installation=mock_installation,
                event="issue.resolved",
                data={"issue": {}},
            )

        # Should NOT have called record_halt
        mock_lifecycle.record_halt.assert_not_called()

        # Should NOT have attempted to send the webhook
        mock_send_request.assert_not_called()

    @patch("sentry.sentry_apps.tasks.sentry_apps.send_and_save_webhook_request")
    @patch("sentry.sentry_apps.tasks.sentry_apps.app_service")
    @patch("sentry.sentry_apps.tasks.sentry_apps._load_service_hook")
    @patch("sentry.sentry_apps.tasks.sentry_apps.SentryAppInteractionEvent")
    def test_send_webhooks_proceeds_normally_when_servicehook_exists(
        self,
        mock_interaction_event,
        mock_load_service_hook,
        mock_app_service,
        mock_send_request,
    ):
        """
        When ServiceHook exists, send_webhooks should proceed with normal webhook delivery.
        """
        # Setup mocks
        mock_lifecycle = MagicMock()
        mock_context = MagicMock()
        mock_context.__enter__ = MagicMock(return_value=mock_lifecycle)
        mock_context.__exit__ = MagicMock(return_value=False)
        mock_interaction_event.return_value.capture.return_value = mock_context

        # ServiceHook exists with matching event
        mock_servicehook = MagicMock()
        mock_servicehook.events = ["issue.resolved"]
        mock_load_service_hook.return_value = mock_servicehook

        # Create a mock installation
        mock_installation = MagicMock()
        mock_installation.id = 123
        mock_installation.organization_id = 456
        mock_installation.sentry_app.id = 789
        mock_installation.sentry_app.events = ["issue.resolved"]
        mock_installation.sentry_app.webhook_url = "https://example.com/webhook"

        from sentry.sentry_apps.tasks.sentry_apps import send_webhooks

        send_webhooks(
            installation=mock_installation,
            event="issue.resolved",
            data={"issue": {}},
        )

        # Should NOT have checked installation existence (servicehook was found)
        mock_app_service.installation_by_id.assert_not_called()

        # Should NOT have called record_halt
        mock_lifecycle.record_halt.assert_not_called()

        # Should have attempted to send the webhook
        mock_send_request.assert_called_once()
