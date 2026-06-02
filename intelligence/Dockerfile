# Stage 1: Build stage
FROM python:3.11-slim AS builder

WORKDIR /app
COPY requirements.txt .

RUN apt-get update && apt-get install -y --no-install-recommends gcc g++ \
    && pip install --user --no-cache-dir -r requirements.txt

# Stage 2: Runtime stage
FROM python:3.11-slim

WORKDIR /app

# Create appuser
RUN useradd -m -u 1000 appuser

# Copy only the installed packages from builder
COPY --from=builder /root/.local /home/appuser/.local
ENV PATH=/home/appuser/.local/bin:$PATH
ENV PYTHONPATH=/app/src/main

# Pre-create cache dir with correct permissions
RUN mkdir -p /app/hf_cache && chown -R appuser:appuser /app/hf_cache

COPY --chown=appuser:appuser . .

USER appuser

# Environment variables for memory optimization
ENV HF_HOME=/app/hf_cache \
    HF_HUB_OFFLINE=0 \
    TRANSFORMERS_OFFLINE=0 \
    OMP_NUM_THREADS=1 \
    MKL_NUM_THREADS=1

# Pre-load model
# RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('intfloat/multilingual-e5-small')"

EXPOSE 8000
CMD ["uvicorn", "src.main.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
