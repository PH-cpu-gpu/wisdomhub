
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
