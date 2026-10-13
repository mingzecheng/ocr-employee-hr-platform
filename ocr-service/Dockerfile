FROM python:3.11-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    OCR_ENV=production \
    OCR_ENGINE=paddle \
    OCR_HOST=0.0.0.0 \
    OCR_PORT=8000 \
    OCR_DATA_DIR=/var/lib/hr-platform/ocr \
    PADDLE_PDX_CACHE_HOME=/var/cache/paddleocr

RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-noto-cjk libgl1 libglib2.0-0 libgomp1 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --create-home --shell /usr/sbin/nologin app \
    && mkdir -p /var/lib/hr-platform/ocr /var/cache/paddleocr \
    && chown -R 10001:10001 /var/lib/hr-platform /var/cache/paddleocr

WORKDIR /app
COPY requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app

USER 10001:10001
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
