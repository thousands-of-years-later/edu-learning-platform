"""阿里云 OSS：签名 URL 与 PPT 上传。"""
from urllib.parse import unquote, urlparse

import oss2

from . import config


def _bucket() -> oss2.Bucket:
    config.require("OSS_ACCESS_KEY_ID", config.OSS_ACCESS_KEY_ID)
    config.require("OSS_ACCESS_KEY_SECRET", config.OSS_ACCESS_KEY_SECRET)
    auth = oss2.Auth(config.OSS_ACCESS_KEY_ID, config.OSS_ACCESS_KEY_SECRET)
    return oss2.Bucket(auth, config.OSS_ENDPOINT, config.OSS_BUCKET)


def sign_url(url_or_key: str, expires: int = 3600) -> str:
    """OSS 地址转签名 URL；非 OSS 地址原样返回。"""
    if not url_or_key:
        return url_or_key
    if url_or_key.startswith("oss://"):
        key = url_or_key[6:].split("/", 1)[1] if "/" in url_or_key[6:] else ""
        return _bucket().sign_url("GET", key, expires, slash_safe=True)

    if url_or_key.startswith("http://") or url_or_key.startswith("https://"):
        parsed = urlparse(url_or_key)
        # 项目里的 /oss-preview/{key} 是后端代理地址，真实对象 key 在 /oss-preview/ 后面
        if parsed.path.startswith("/oss-preview/"):
            key = unquote(parsed.path[len("/oss-preview/"):])
            return _bucket().sign_url("GET", key, expires, slash_safe=True)
        expected_host = f"{config.OSS_BUCKET}.{config.OSS_ENDPOINT}"
        if parsed.netloc != expected_host and not parsed.netloc.endswith("." + config.OSS_ENDPOINT):
            return url_or_key
        key = unquote(parsed.path.lstrip("/"))
        return _bucket().sign_url("GET", key, expires, slash_safe=True)

    return _bucket().sign_url("GET", url_or_key.lstrip("/"), expires, slash_safe=True)


def read_text(key: str) -> str:
    """读取小文本对象（字幕等），用于回填元信息。"""
    return _bucket().get_object(key).read().decode("utf-8", "ignore")


def exists(key: str) -> bool:
    """判断对象是否已存在，用于字幕等产物缓存。"""
    try:
        return bool(_bucket().object_exists(key))
    except Exception:
        return False


def upload_file(local_path: str, key: str) -> str:
    _bucket().put_object_from_file(key, local_path)
    # bucket 为公共读：直接返回可长期访问的直连地址。
    # 不再签名 —— 签名 URL 只有 1 小时有效期，客户端下载 PPT 会被 403。
    host = config.OSS_ENDPOINT.replace("https://", "").replace("http://", "").rstrip("/")
    return f"https://{config.OSS_BUCKET}.{host}/{key}"
