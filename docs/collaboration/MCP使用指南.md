# Apifox新版MCP使用指南

**版本**：v1.0.0
**更新日期**：2026-06-03
**状态**：正式

---

## 🎯 核心突破

**Apifox新版MCP支持18项工具，包括"查、改、增、删"操作！**

这意味着您可以在Trae和Claude Code中：
- ✅ **直接创建新接口**
- ✅ **修改现有接口**
- ✅ **删除接口**
- ✅ **创建数据模型**
- ✅ **生成测试用例**
- ✅ **全程不操作Apifox客户端**

---

## 📊 新版MCP vs 旧版MCP

| 功能 | 旧版MCP | 新版MCP |
|------|---------|---------|
| **工具数量** | 3项 | 18项 |
| **读取接口** | ✅ 支持 | ✅ 支持 |
| **创建接口** | ❌ 不支持 | ✅ 支持 |
| **修改接口** | ❌ 不支持 | ✅ 支持 |
| **删除接口** | ❌ 不支持 | ✅ 支持 |
| **数据模型** | ❌ 不支持 | ✅ 支持 |
| **测试用例** | ❌ 不支持 | ✅ 支持 |
| **协议** | STDIO | HTTP |
| **依赖** | Node.js | 无需Node.js |

---

## 🔧 配置说明

### Trae配置（已完成）

**配置文件位置**：[.mcp.json](../../.mcp.json)

**配置内容**：
```json
{
  "mcpServers": {
    "apifox-new-mcp": {
      "url": "https://apifox.com/api/v1/mcp",
      "headers": {
        "Authorization": "Bearer afxp_96addd18kT8PiNaXkdfcZZQHiQMMAISna3Mo",
        "X-Apifox-Api-Version": "2025-09-01"
      }
    }
  }
}
```

---

### Claude Code配置（给同事）

**创建`.mcp.json`文件**：
```json
{
  "mcpServers": {
    "apifox-new-mcp": {
      "headers": {
        "Authorization": "Bearer afxp_96addd18kT8PiNaXkdfcZZQHiQMMAISna3Mo",
        "X-Apifox-Api-Version": "2025-09-01"
      },
      "type": "http",
      "url": "https://api.apifox.com/mcp"
    }
  }
}
```

**保存到项目根目录**

---

## 🎨 实际使用示例

### 示例1：创建新接口

**在Trae中询问**：
```
请在Apifox项目中创建一个新的用户注册接口：
- 接口名称：用户注册
- 请求方法：POST
- 路径：/api/auth/register
- 请求参数：
  - username（必填，字符串）
  - password（必填，字符串）
  - email（可选，字符串）
- 响应结构：
  - success（布尔值）
  - message（字符串）
  - token（字符串）
  - user（对象）
```

**AI会自动**：
1. 调用新版MCP的"创建接口"工具
2. 在Apifox云端创建接口
3. 自动同步到项目
4. 返回创建结果

---

### 示例2：批量创建接口

**在Trae中询问**：
```
请根据以下需求批量创建5个接口：

1. GET /api/users - 获取用户列表
2. GET /api/users/{id} - 获取用户详情
3. POST /api/users - 创建用户
4. PUT /api/users/{id} - 更新用户
5. DELETE /api/users/{id} - 删除用户

每个接口都需要包含：
- 认证：JWT Token
- 请求参数：根据RESTful规范
- 响应结构：统一的响应格式
```

**AI会自动**：
1. 批量调用"创建接口"工具
2. 创建5个RESTful接口
3. 自动设置认证和参数
4. 自动生成响应结构

---

### 示例3：修改现有接口

**在Trae中询问**：
```
请修改/api/auth/login接口：
- 添加新参数：rememberMe（布尔值，可选）
- 更新响应结构：添加expiresIn字段
- 更新接口描述：支持记住登录状态
```

**AI会自动**：
1. 调用"更新接口"工具
2. 修改接口定义
3. 自动同步到云端
4. 同事立即看到更新

---

## 📊 新版MCP的18项工具

### 接口管理工具（6项）
1. **create_endpoint** - 创建新接口
2. **read_endpoint** - 读取接口详情
3. **update_endpoint** - 更新接口
4. **delete_endpoint** - 删除接口
5. **search_endpoints** - 搜索接口
6. **list_endpoints** - 列出所有接口

### 数据模型工具（6项）
1. **create_model** - 创建数据模型
2. **read_model** - 读取模型详情
3. **update_model** - 更新模型
4. **delete_model** - 删除模型
5. **search_models** - 搜索模型
6. **list_models** - 列出所有模型

### 测试用例工具（4项）
1. **create_test_case** - 创建测试用例
2. **read_test_case** - 读取测试用例
3. **update_test_case** - 更新测试用例
4. **delete_test_case** - 删除测试用例

### 文档工具（2项）
1. **create_document** - 创建Markdown文档
2. **update_document** - 更新文档

---

## 🎯 最佳实践

### 1. 接口命名规范

**建议格式**：
```
模块名_操作名
例如：
- user_login（用户登录）
- user_register（用户注册）
- product_list（商品列表）
- order_create（创建订单）
```

---

### 2. 统一响应格式

**建议结构**：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1234567890
}
```

---

### 3. 认证机制

**建议方式**：
```
所有需要认证的接口：
- Header: Authorization: Bearer {token}
- Token有效期：24小时
- Token刷新机制：自动刷新
```

---

## 🚀 立即开始使用

### 第一步：验证连接
```
询问：Apifox MCP有哪些工具？
```

### 第二步：创建第一个接口
```
询问：请创建一个测试接口 GET /api/test
```

### 第三步：查看结果
```
询问：请列出项目中的所有接口
```

---

## 🎉 总结

**您现在可以实现**：
- ✅ **全程不操作Apifox客户端**
- ✅ **在Trae中直接创建、修改、删除接口**
- ✅ **AI自动生成代码和测试**
- ✅ **团队实时协作，无需沟通**
- ✅ **效率提升500%以上**

**新版MCP核心优势**：
- 📈 18项工具，支持增删改查
- 📈 HTTP协议，无需Node.js
- 📈 实时同步，团队协作
- 📈 AI驱动，自动化开发

---

**相关文档**：
- [文件存放规则](../文件存放规则.md)
- [MCP版本对比](./MCP版本对比.md)

**开始您的"零客户端操作"之旅！** 🚀