from openconnect.web.auth import make_session, valid_session


def test_session_is_bound_to_the_password_and_expires():
    token = make_session("secret", now=1000)
    assert valid_session(token, "secret", now=1000)
    assert not valid_session(token, "wrong", now=1000)
    assert not valid_session(token, "secret", now=1000 + 8 * 24 * 60 * 60)
    assert not valid_session(token, "secret", now=999)  # not issued in the future
    assert not valid_session("garbage", "secret", now=1000)
    tampered = token[:-1] + ("0" if token[-1] != "0" else "1")
    assert not valid_session(tampered, "secret", now=1000)
