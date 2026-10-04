
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

// 版本快照：主键是 (articleId, versionNo) 二元组，_id 装不下，故用自动 _id + 唯一复合索引。
// 只增不改，没有 updateTime。
contentDb.createCollection('article_version_content', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['articleId', 'versionNo', 'markdown', 'html', 'wordCount', 'createTime'],
            properties: {
                articleId: { bsonType: 'long', description: '雪花 ID' },
                versionNo: { bsonType: 'int', description: '版本号' },
                markdown: { bsonType: 'string', description: 'Markdown 原文' },
                html: { bsonType: 'string', description: '渲染后 HTML' },
                wordCount: { bsonType: 'int', description: '字数' },
                createTime: { bsonType: 'date' }
            }
        }
    }
});

contentDb.article_version_content.createIndex(
    { articleId: 1, versionNo: 1 },
    { unique: true, name: 'uk_article_version' }
);
