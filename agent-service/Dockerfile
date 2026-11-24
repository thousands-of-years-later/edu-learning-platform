FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
# 国内服务器走阿里云 PyPI 镜像，避免 pythonhosted 超时；可用 --build-arg 覆盖
ARG PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/
RUN pip install --no-cache-dir --index-url ${PIP_INDEX_URL} -r requirements.txt

COPY app ./app
COPY templates ./templates

EXPOSE 8100
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8100"]
