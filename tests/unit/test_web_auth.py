from openconnect.web.auth import make_session, same_text, valid_session


def test_session_is_bound_to_the_password_and_expires():
    token = make_session("secret", now=1000)
    assert valid_session(token, "secret", now=1000)
    assert not valid_session(token, "wrong", now=1000)
    assert not valid_session(token, "secret", now=1000 + 8 * 24 * 60 * 60)
    assert not valid_session(token, "secret", now=999)  # not issued in the future
    assert not valid_session("garbage", "secret", now=1000)
    tampered = token[:-1] + ("0" if token[-1] != "0" else "1")
    assert not valid_session(tampered, "secret", now=1000)


def test_a_password_or_cookie_with_accents_is_compared_without_crashing():
    # hmac.compare_digest refuses non-ASCII str arguments: such a password never logged in
    password = "wachtwoord€é"
    token = make_session(password, now=1000)
    assert valid_session(token, password, now=1000)
    assert not valid_session(token, "wachtwoord€e", now=1000)
    # a crafted cookie with a non-ASCII signature is simply invalid (it was an unhandled TypeError)
    assert not valid_session("1000.sïgnature", password, now=1000)
    assert same_text("é€", "é€") and not same_text("é€", "e€")


def test_the_cookie_is_signed_with_a_key_made_from_the_password_not_the_password_itself():
    import hashlib
    import hmac

    token = make_session("secret", now=1000)
    issued, signature = token.split(".")
    # signing with the bare password (how it was before) no longer produces a valid cookie
    naive = hmac.new(b"secret", issued.encode(), hashlib.sha256).hexdigest()
    assert signature != naive
    assert not valid_session(f"{issued}.{naive}", "secret", now=1000)
