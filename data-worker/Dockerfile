FROM python:3.10-slim
WORKDIR /app
# Copy only requirements first
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
# Now copy the code
COPY . .
CMD ["python", "nse_worker.py"]