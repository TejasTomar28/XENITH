FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
# Synthetic data and artifacts are gitignored, so generate them in-image (same parameters as README).
RUN python synthetic_data.py synthetic/labelled.csv --count 10000 --seed 42 --fraud-rate 0.12 \
    && python train.py --data synthetic/labelled.csv --artifacts artifacts
EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
