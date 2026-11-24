"""PPT 生成：LLM 输出大纲 JSON，python-pptx 套模板，OSS 上传。"""
import datetime
import json
import re
import uuid
from pathlib import Path
from typing import Any, Dict, List

from pptx import Presentation
from pptx.shapes.group import GroupShape

from . import config, llm, oss_client

OUTLINE_SYSTEM = """你是学习平台的 PPT 大纲助手。根据主题输出严格 JSON，不要输出 JSON 以外的内容。
JSON 结构：
{
  "title": "PPT 标题",
  "subtitle": "副标题",
  "sections": [{"title": "章节标题", "summary": "一句话说明"}],
  "highlights": ["亮点1", "亮点2", "亮点3"],
  "keyPoints": ["要点1", "要点2", "要点3", "要点4"],
  "closing": "谢谢观看"
}
要求：
- sections 固定 4 个，title 不超过 12 个汉字，summary 不超过 40 字；
- highlights 固定 3 个，每个不超过 25 字；
- keyPoints 固定 4 个，每个不超过 30 字；
- 内容要具体，适合直接放进 PPT。
"""


async def generate(topic: str, pages: int = 6, user_id: int = 0, task_id: str | None = None) -> Dict[str, Any]:
    topic = (topic or "").strip()
    if not topic:
        raise ValueError("PPT 主题不能为空")

    outline_text = await llm.chat(
        [
            {"role": "system", "content": OUTLINE_SYSTEM},
            {"role": "user", "content": f"主题：{topic}\n页数参考：{pages}"},
        ],
        temperature=0.4,
        max_tokens=1200,
    )
    outline = _normalize(llm.extract_json(outline_text), topic)

    task_key = task_id or uuid.uuid4().hex[:16]
    output_dir: Path = config.PPT_OUTPUT_DIR
    output_dir.mkdir(parents=True, exist_ok=True)
    local_path = output_dir / f"{task_key}.pptx"
    slide_count = _render(outline, local_path)

    key = f"{config.OSS_PPT_PREFIX}/{user_id}/{task_key}.pptx"
    url = oss_client.upload_file(str(local_path), key)
    return {
        "url": url,
        "fileName": f"{outline['title']}.pptx",
        "slides": slide_count,
        "outline": outline,
    }


def _normalize(raw: Dict[str, Any], topic: str) -> Dict[str, Any]:
    title = str(raw.get("title") or topic)[:40]
    subtitle = str(raw.get("subtitle") or "智能学习平台 · AI 生成")[:50]

    sections = []
    for item in (raw.get("sections") or [])[:4]:
        if isinstance(item, dict):
            sections.append({
                "title": str(item.get("title") or "")[:12],
                "summary": str(item.get("summary") or "")[:40],
            })
    while len(sections) < 4:
        sections.append({"title": f"章节 {len(sections) + 1}", "summary": ""})

    highlights = [str(x)[:25] for x in (raw.get("highlights") or [])[:3]]
    while len(highlights) < 3:
        highlights.append("")

    key_points = [str(x)[:30] for x in (raw.get("keyPoints") or [])[:4]]
    while len(key_points) < 4:
        key_points.append("")

    return {
        "title": title,
        "subtitle": subtitle,
        "sections": sections,
        "highlights": highlights,
        "keyPoints": key_points,
        "closing": str(raw.get("closing") or "谢谢观看")[:20],
    }


def _render(outline: Dict[str, Any], output_path: Path) -> int:
    if not config.PPT_TEMPLATE.exists():
        raise FileNotFoundError(f"PPT 模板不存在: {config.PPT_TEMPLATE}")

    prs = Presentation(str(config.PPT_TEMPLATE))
    if len(prs.slides) < 21:
        raise ValueError("PPT 模板页数不足，至少需要 21 页")

    today = datetime.date.today().strftime("%Y/%m/%d")
    _fill_cover(prs.slides[0], outline, today)
    _fill_contents(prs.slides[1], outline)
    _fill_section(prs.slides[2], outline["sections"][0])
    _fill_highlights(prs.slides[5], outline)
    _fill_keypoints(prs.slides[8], outline)
    _fill_closing(prs.slides[19], outline, today)

    keep = {0, 1, 2, 5, 8, 19}
    remove = [i for i in range(len(prs.slides)) if i not in keep]
    _delete_slides(prs, remove)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    prs.save(str(output_path))
    return len(prs.slides)


def _text_shapes(shapes) -> List[Any]:
    result = []
    for shape in shapes:
        if isinstance(shape, GroupShape):
            result.extend(_text_shapes(shape.shapes))
        elif getattr(shape, "has_text_frame", False):
            result.append(shape)
    return result


def _set_text(shape, value: str) -> None:
    value = (value or "").strip()
    tf = shape.text_frame
    if not value:
        return
    paragraph = tf.paragraphs[0]
    if paragraph.runs:
        paragraph.runs[0].text = value
        for run in paragraph.runs[1:]:
            run.text = ""
    else:
        paragraph.text = value
    for extra in tf.paragraphs[1:]:
        extra._p.getparent().remove(extra._p)


def _fill_cover(slide, outline: Dict[str, Any], today: str) -> None:
    for shape in _text_shapes(slide.shapes):
        text = shape.text_frame.text.strip()
        if "总结模板" in text or "PPT模板" in text:
            _set_text(shape, outline["title"])
        elif "POWERPOINT" in text.upper():
            _set_text(shape, outline["subtitle"])
        elif "汇报人" in text:
            _set_text(shape, "智能学习平台")
        elif re.search(r"20[0-9X]{2}[/-]\d{1,2}[/-]\d{1,2}", text):
            _set_text(shape, today)


def _fill_contents(slide, outline: Dict[str, Any]) -> None:
    index = 0
    for shape in _text_shapes(slide.shapes):
        text = shape.text_frame.text.strip()
        if "章节标题" in text:
            _set_text(shape, outline["sections"][index]["title"] if index < 4 else "")
            index += 1


def _fill_section(slide, section: Dict[str, str]) -> None:
    for shape in _text_shapes(slide.shapes):
        text = shape.text_frame.text.strip()
        if "章节标题" in text:
            _set_text(shape, section["title"])
        elif text.startswith("Lorem ipsum"):
            _set_text(shape, section["summary"])


def _fill_highlights(slide, outline: Dict[str, Any]) -> None:
    body_index = 0
    heading_index = 0
    for shape in _text_shapes(slide.shapes):
        text = shape.text_frame.text.strip()
        if "本页标题" in text or "INSERT TITLE HERE" in text.upper():
            _set_text(shape, "核心亮点")
        elif "小标题" in text:
            if heading_index < 3:
                _set_text(shape, f"亮点 {heading_index + 1}")
                heading_index += 1
        elif text.startswith("Lorem ipsum"):
            if body_index < 3:
                _set_text(shape, outline["highlights"][body_index])
                body_index += 1
            else:
                shape.text_frame.text = ""


def _fill_keypoints(slide, outline: Dict[str, Any]) -> None:
    point_index = 0
    for shape in _text_shapes(slide.shapes):
        text = shape.text_frame.text.strip()
        if "本页标题" in text or "INSERT TITLE HERE" in text.upper():
            _set_text(shape, "关键要点")
        elif text.startswith("Nulla consequat"):
            if point_index < 4:
                _set_text(shape, outline["keyPoints"][point_index])
                point_index += 1


def _fill_closing(slide, outline: Dict[str, Any], today: str) -> None:
    for shape in _text_shapes(slide.shapes):
        text = shape.text_frame.text.strip()
        if "谢谢观看" in text or "谢谢" in text:
            _set_text(shape, outline["closing"])
        elif "POWERPOINT" in text.upper():
            _set_text(shape, "AI GENERATED PRESENTATION")
        elif "汇报人" in text:
            _set_text(shape, "智能学习平台")
        elif re.search(r"20[0-9X]{2}[/-]\d{1,2}[/-]\d{1,2}", text):
            _set_text(shape, today)


def _delete_slides(prs, indexes: List[int]) -> None:
    """按索引删除幻灯片；倒序删除，避免下标漂移。"""
    sld_id_list = prs.slides._sldIdLst
    all_ids = list(sld_id_list)
    for index in sorted(indexes, reverse=True):
        slide_id = all_ids[index]
        r_id = slide_id.rId
        prs.part.drop_rel(r_id)
        sld_id_list.remove(slide_id)
