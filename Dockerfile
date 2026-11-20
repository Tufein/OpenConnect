FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    OPENCONNECT_HOST=0.0.0.0 \
    OPENCONNECT_DB=/data/openconnect.db

WORKDIR /app
COPY pyproject.toml README.md LICENSE ./
COPY src ./src
RUN pip install . \
    && useradd --system --uid 10001 openconnect \
    && mkdir /data && chown openconnect /data

USER openconnect
VOLUME /data
EXPOSE 8000
# asks the port the server really listens on, so a changed OPENCONNECT_PORT does not make the container "unhealthy"
HEALTHCHECK --interval=30s --timeout=5s \
    CMD python -c "import os, urllib.request; urllib.request.urlopen('http://127.0.0.1:%s/healthz' % os.environ.get('OPENCONNECT_PORT', '8000'))"
CMD ["openconnect", "serve"]
