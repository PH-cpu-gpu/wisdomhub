package com.wisdomhub.content.util;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Markdown → HTML。
 *
 * <p>渲染完必须过白名单消毒：CommonMark 允许内联原始 HTML 通过，不过一道的话，作者写一段
 * {@code <script>} 会被渲染成真的 script 标签、存进 MongoDB，再由详情页原样返回给所有访客 ——
 * 存储型 XSS。
 *
 * <p>产出是"消毒后的 HTML"，只保证安全，不保证和前端编辑器的预览逐字符一致（两边是不同实现）。
 */
@Component
public class MarkdownRenderer {

    /** 只给 jsoup 的协议校验当解析基准（非空才肯校验相对路径），preserveRelativeLinks(true) 保证它不会写进产物 */
    private static final String BASE_URI = "https://wisdomhub.local/";

    private final Parser parser;
    private final HtmlRenderer renderer;
    private final Safelist safelist;

    public MarkdownRenderer() {
        MutableDataSet options = new MutableDataSet();
        // 只开技术文章用得上的三个：表格、删除线、裸 URL 自动成链接
        options.set(Parser.EXTENSIONS, List.of(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                AutolinkExtension.create()));
        this.parser = Parser.builder(options).build();
        this.renderer = HtmlRenderer.builder(options).build();
        this.safelist = buildSafelist();
    }

    public String render(String markdown) {
        String html = renderer.render(parser.parse(markdown));
        // prettyPrint(false)：默认的缩进重排会把 <pre> 里的代码块弄乱
        return Jsoup.clean(html, BASE_URI, safelist, new Document.OutputSettings().prettyPrint(false));
    }

    /** 取纯文本，给摘要和字数统计用 */
    public String toPlainText(String html) {
        return Jsoup.parse(html).text();
    }

    private static Safelist buildSafelist() {
        return Safelist.none()
                .addTags("p", "br", "hr", "h1", "h2", "h3", "h4", "h5", "h6",
                        "ul", "ol", "li", "blockquote", "pre", "code",
                        "em", "strong", "del", "a", "img",
                        "table", "thead", "tbody", "tr", "th", "td", "sup", "sub", "kbd", "span")
                .addAttributes("a", "href", "title")
                .addAttributes("img", "src", "alt", "title")
                .addAttributes("th", "align")
                .addAttributes("td", "align")
                // 前端代码高亮靠 <code class="language-xxx">，只放开这一个属性
                .addAttributes("code", "class")
                // javascript: 之类的协议不在名单里，会被直接丢掉
                .addProtocols("a", "href", "http", "https", "mailto")
                .addProtocols("img", "src", "http", "https")
                // 站内互链是相对路径（如 /article/123），不保留的话会被当成非法协议删掉
                .preserveRelativeLinks(true);
    }
}
