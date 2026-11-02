package com.ruoyi.community.utils;

/**
 * 文章正文的轻量处理工具（列表摘要 / 首图提取）
 *
 * 为什么放后端做：设计稿 17_话题页 和 05_社区页 的卡片要显示「一句话摘要」和「右侧缩略图」，
 * 而正文存的是 Markdown 或富文本 HTML，直接截前 N 个字符会把 ``` 、`<p>`、`![](...)` 也截进去，
 * 前端每个端都要各写一遍清洗逻辑。这里统一在返回列表时算好。
 *
 * 约定：
 *   · 只处理【列表展示】用的短文本，不改动库里的正文；
 *   · 摘要按"清洗后的纯文本"截断，超长补省略号；
 *   · 首图取正文里第一张图片（Markdown ![]() 或 HTML <img src>），没有返回 null；
 *   · 图片地址按库里原样返回（可能是 /profile/... 相对路径，也可能是 OSS 完整地址），
 *     与其它图片字段的口径一致，由前端决定是否拼域名。
 *
 * @author edu
 */
public class PostContentUtils
{
    /** 列表摘要默认截取长度 */
    public static final int DEFAULT_SUMMARY_LENGTH = 60;

    private PostContentUtils()
    {
    }

    /**
     * 把正文（Markdown / HTML）清洗成一句话摘要
     *
     * @param content 正文（可以是列表查询时截取的片段）
     * @param maxLen  最多保留多少字（超出补省略号）
     */
    public static String summarize(String content, int maxLen)
    {
        if (content == null || content.isEmpty())
        {
            return null;
        }
        String text = content;
        // 代码块、脚本、样式整体丢掉（在摘要里显示代码没有意义）
        text = text.replaceAll("(?is)```[\\s\\S]*?```", " ");
        text = text.replaceAll("(?is)<script.*?</script>", " ");
        text = text.replaceAll("(?is)<style.*?</style>", " ");
        // Markdown 图片整段丢掉；Markdown 链接只保留文字
        text = text.replaceAll("(?is)!\\[[^\\]]*\\]\\([^)]*\\)", " ");
        text = text.replaceAll("(?is)\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        // HTML 标签去掉（含 <img>，摘要里不需要图片）
        text = text.replaceAll("(?is)<[^>]+>", " ");
        // 行首的 Markdown 标题标记 / 引用标记
        text = text.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", " ");
        text = text.replaceAll("(?m)^\\s{0,3}>\\s*", " ");
        // 行内强调、行内代码符号
        text = text.replaceAll("[`*_~|]", " ");
        // 压缩空白
        text = text.replaceAll("\\s+", " ").trim();

        if (text.isEmpty())
        {
            return null;
        }
        if (text.length() > maxLen)
        {
            text = text.substring(0, maxLen).trim() + "…";
        }
        return text;
    }

    /**
     * 取正文里的第一张图片地址（Markdown 或 HTML 都认）
     *
     * @return 图片地址（库里原样，可能是相对路径）；没有图片返回 null
     */
    public static String firstImage(String content)
    {
        if (content == null || content.isEmpty())
        {
            return null;
        }
        // Markdown: ![alt](url) —— 允许 url 前后有空格
        java.util.regex.Matcher md = java.util.regex.Pattern
                .compile("!\\[[^\\]]*\\]\\(\\s*([^\\s)]+)").matcher(content);
        if (md.find())
        {
            return md.group(1);
        }
        // HTML: <img src="url"> / <img src='url'>
        java.util.regex.Matcher html = java.util.regex.Pattern
                .compile("(?i)<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']").matcher(content);
        if (html.find())
        {
            return html.group(1);
        }
        return null;
    }
}
