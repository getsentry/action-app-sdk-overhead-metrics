"""
Tests for the race condition fix where webhook tasks run after
SentryAppInstallation is deleted and ServiceHook is cascade-deleted.

When a Sentry App is uninstalled from an organization:
1. SentryAppInstallation is deleted
2. ServiceHook is cascade-deleted via FK
3. But stale/queued webhook tasks may still reference the installation_id

Before the fix, send_webhooks would raise SentryAppSentryError with
missing_servicehook. After the fix, it detects the deleted installation
and gracefully halts instead of raising an error.
"""

from unittest.mock import MagicMock, patch

import pytest

from sentry.sentry_apps.tasks.sentry_apps import (
    _is_installation_deleted,
    send_webhooks,
    send_resource_change_webhook,
)


class TestIsInstallationDeleted:
    """Tests for the _is_installation_deleted helper function."""

    @patch("sentry.sentry_apps.tasks.sentry_apps.SentryAppInstallation.objects")
    def test_returns_true_when_installation_not_found(self, mock_objects):
        """Installation that doesn't exist at all is considered deleted."""
        mock_objects.filter.return_value.exists.return_value = False
        assert _is_installation_deleted(999) is True
        mock_objects.filter.assert_called_once_with(
            id=999,
            status=0,  # SentryAppInstallationStatus.INSTALLED
            date_deleted=None,
        )

    @patch("sentry.sentry_apps.tasks.sentry_apps.SentryAppInstallation.objects")
    def test_returns_false_when_installation_exists(self, mock_objects):
        """Active installation is not considered deleted."""
        mock_objects.filter.return_value.exists.return_value = True
        assert _is_installation_deleted(123) is False

    @patch("sentry.sentry_apps.tasks.sentry_apps.SentryAppInstallation.objects")
    def test_returns_true_when_installation_soft_deleted(self, mock_objects):
        """Installation with date_deleted set won't match the filter, so considered deleted."""
        # filter with date_deleted=None won't match a soft-deleted record
        mock_objects.filter.return_value.exists.return_value = False
        assert _is_installation_deleted(456) is True


class TestSendWebhooksDeletedInstallation:
    """Tests for the race condition in send_webhooks when installation is deleted."""

    @patch("sentry.sentry_apps.tasks.sentry_apps._is_installation_deleted")
    @patch("sentry.sentry_apps.tasks.sentry_apps._load_service_hook")
    @patch("sentry.sentry_apps.tasks.sentry_apps.SentryAppInteractionEvent")
    def test_gracefully_halts_when_servicehook_missing_and_installation_deleted(
        self, mock_interaction_event, mock_load_hook, mock_is_deleted
    ):
        """
        When ServiceHook is missing because the installation was deleted (race condition),
        send_webhooks should gracefully halt instead of raising SentryAppSentryError.
        """
        # Setup: ServiceHook not found (cascade-deleted)
        mock_load_hook.return_value = None
        # Setup: Installation was deleted
        mock_is_deleted.return_value = True

        # Setup mock lifecycle
        mock_lifecycle = MagicMock()
        mock_context = MagicMock()
        mock_context.__enter__ = MagicMock(return_value=mock_lifecycle)
        mock_context.__exit__ = MagicMock(return_value=False)
        mock_interaction_event.return_value.capture.return_value = mock_context

        # Setup mock installation
        mock_installation = MagicMock()
        mock_installation.id = 42
        mock_installation.organization_id = 1
        mock_installation.sentry_app.id = 10

        # Should NOT raise - should gracefully return
        send_webhooks(installation=mock_installation, event="issue.resolved", data={})

        # Verify it recorded a halt with MISSING_INSTALLATION reason
        mock_lifecycle.record_halt.assert_called_once()

    @patch("sentry.sentry_apps.tasks.sentry_apps._is_installation_deleted")
    @patch("sentry.sentry_apps.tasks.sentry_apps._load_service_hook")
    @patch("sentry.sentry_apps.tasks.sentry_apps.SentryAppInteractionEvent")
    def test_still_raises_when_servicehook_missing_but_installation_active(
        self, mock_interaction_event, mock_load_hook, mock_is_deleted
    ):
        """
        When ServiceHook is missing but the installation is still active,
        send_webhooks should raise SentryAppSentryError as before (not a race condition).
        """
        from sentry.sentry_apps.utils.errors import SentryAppSentryError

        # Setup: ServiceHook not found
        mock_load_hook.return_value = None
        # Setup: Installation is still active (not deleted)
        mock_is_deleted.return_value = False

        # Setup mock lifecycle
        mock_lifecycle = MagicMock()
        mock_context = MagicMock()
        mock_context.__enter__ = MagicMock(return_value=mock_lifecycle)
        mock_context.__exit__ = MagicMock(return_value=False)
        mock_interaction_event.return_value.capture.return_value = mock_context

        # Setup mock installation
        mock_installation = MagicMock()
        mock_installation.id = 42
        mock_installation.organization_id = 1
        mock_installation.sentry_app.id = 10
        mock_installation.sentry_app.events = ["issue.resolved"]
        mock_installation.sentry_app.webhook_url = "https://example.com/webhook"
        mock_installation.uuid = "test-uuid"

        # Should raise SentryAppSentryError since installation is active but hook is missing
        with pytest.raises(SentryAppSentryError):
            send_webhooks(installation=mock_installation, event="issue.resolved", data={})


class TestSendResourceChangeWebhookDeletedInstallation:
    """Tests for the race condition in send_resource_change_webhook when installation is deleted."""

    @patch("sentry.sentry_apps.tasks.sentry_apps._is_installation_deleted")
    @patch("sentry.sentry_apps.tasks.sentry_apps.app_service")
    @patch("sentry.sentry_apps.tasks.sentry_apps.SentryAppInteractionEvent")
    def test_gracefully_halts_when_installation_deleted(
        self, mock_interaction_event, mock_app_service, mock_is_deleted
    ):
        """
        When installation_by_id returns None and installation is deleted,
        send_resource_change_webhook should gracefully halt.
        """
        # Setup: installation_by_id returns None (stale cache cleared)
        mock_app_service.installation_by_id.return_value = None
        # Setup: Installation was deleted
        mock_is_deleted.return_value = True

        # Setup mock lifecycle
        mock_lifecycle = MagicMock()
        mock_context = MagicMock()
        mock_context.__enter__ = MagicMock(return_value=mock_lifecycle)
        mock_context.__exit__ = MagicMock(return_value=False)
        mock_interaction_event.return_value.capture.return_value = mock_context

        # Should NOT raise - should gracefully return
        send_resource_change_webhook(
            installation_id=42,
            event="issue.resolved",
            data={"issue": {}},
        )

        # Verify it recorded a halt
        mock_lifecycle.record_halt.assert_called_once()
