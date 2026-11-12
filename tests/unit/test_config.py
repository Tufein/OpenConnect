from pathlib import Path

import pytest

from openconnect.config import ConfigError, Settings


def test_defaults_are_safe_and_offline():
    s = Settings.from_env({})
    assert s.garmin_mode == "mock"
    assert s.ai_provider == "auto" and s.ai_model == "claude-opus-5-5"
    assert s.host == "127.0.0.1"  # never listen on all interfaces by default
    assert s.language == "en"
    assert isinstance(s.db_path, Path)
    assert s.webhook_token is None


def test_reads_environment_overrides():
    s = Settings.from_env(
        {
            "OPENCONNECT_DB": "/tmp/x.db",
            "OPENCONNECT_GARMIN_MODE": "live",
            "GARMIN_CLIENT_ID": "cid",
            "GARMIN_CLIENT_SECRET": "sec",
            "OPENCONNECT_BASE_URL": "https://oc.example.org/",
            "ANTHROPIC_API_KEY": "sk-test",
            "OPENCONNECT_AI_MODEL": "claude-sonnet-5-5",
            "OPENCONNECT_AI_EFFORT": "low",
            "OPENCONNECT_LANGUAGE": "nl",
            "OPENCONNECT_WEBHOOK_TOKEN": "tok",
            "OPENCONNECT_PORT": "9000",
        }
    )
    assert s.db_path == Path("/tmp/x.db")
    assert s.garmin_mode == "live" and s.garmin_client_id == "cid"
    assert s.base_url == "https://oc.example.org"  # trailing slash stripped
    assert s.redirect_uri == "https://oc.example.org/api/garmin/callback"
    assert s.anthropic_api_key == "sk-test" and s.ai_model == "claude-sonnet-5-5"
    assert s.ai_effort == "low" and s.language == "nl" and s.webhook_token == "tok"
    assert s.port == 9000


@pytest.mark.parametrize(
    "env",
    [
        {"OPENCONNECT_GARMIN_MODE": "yolo"},
        {"OPENCONNECT_AI_PROVIDER": "gpt"},
        {"OPENCONNECT_AI_EFFORT": "ultra"},
        {"OPENCONNECT_PORT": "not-a-number"},
        {"OPENCONNECT_GARMIN_MODE": "live"},  # live needs client credentials
    ],
)
def test_invalid_configuration_is_rejected_with_a_clear_error(env):
    with pytest.raises(ConfigError):
        Settings.from_env(env)


def test_empty_values_count_as_unset():
    s = Settings.from_env({"ANTHROPIC_API_KEY": "", "OPENCONNECT_WEBHOOK_TOKEN": ""})
    assert s.anthropic_api_key is None and s.webhook_token is None


def test_allowed_hosts_include_the_public_hostname_and_extras():
    s = Settings.from_env(
        {
            "OPENCONNECT_BASE_URL": "https://oc.example.org",
            "OPENCONNECT_ALLOWED_HOSTS": "nas, oc.lan",
        }
    )
    assert {"localhost", "127.0.0.1", "oc.example.org", "nas", "oc.lan"} <= set(s.allowed_hosts)
