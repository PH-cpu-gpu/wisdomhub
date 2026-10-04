
const contentDb = db.getSiblingDB('wisdomhub_content');

// _id 即雪花 articleId

contentDb.createCollection('article_content', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['markdown', 'html', 'wordCount', 'createTime', 'updateTime'],
            properties: {
                _id: { bsonType: 'long', description: '雪花 ID' },
                markdown: { bsonType: 'string', description: 'Markdown 原文' },
                html: { bsonType: 'string', description: '渲染后 HTML' },
                wordCount: { bsonType: 'int', description: '字数' },
                createTime: { bsonType: 'date' },
                updateTime: { bsonType: 'date' }
            }
        }
    }
});

// 草稿：_id 同样是雪花 articleId，一篇一稿。
contentDb.createCollection('article_draft', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['markdown', 'wordCount', 'createTime', 'updateTime'],
            properties: {
                _id: { bsonType: 'long', description: '雪花 ID' },
                markdown: { bsonType: 'string', description: 'Markdown 原文' },
                wordCount: { bsonType: 'int', description: '字数' },
                createTime: { bsonType: 'date' },
                updateTime: { bsonType: 'date' }
            }
        }
    }
});

// 版本快照已取消（2026-10-05）：只保留正文与草稿两个 collection。
// 见技术文档 7.12 与任务表 B1.19 —— 理由是没有 diff 界面的版本历史价值极低，
// 而它把一次发布从两处写变成四处写。
