# Logo 设计规范与集成指南

## 一、Logo 尺寸要求

### 1.1 浏览器标签页 Favicon（Tab 图标）

**标准尺寸**：
- **16×16 像素**：最小尺寸，必须提供
- **32×32 像素**：标准尺寸，推荐
- **48×48 像素**：高分辨率显示
- **180×180 像素**：Apple Touch Icon（iOS 设备）

**文件格式**：
- `.ico`：传统格式，支持多尺寸
- `.png`：现代浏览器推荐
- `.svg`：矢量格式，最佳选择（现代浏览器）

**推荐方案**：
```
favicon.ico          (16×16, 32×32, 48×48 多尺寸)
favicon-32x32.png    (32×32)
favicon-16x16.png    (16×16)
apple-touch-icon.png (180×180)
```

---

### 1.2 主页面 Logo（左侧导航栏）

**显示位置**：左侧导航栏顶部（`.yulu-logo`）

**当前样式**（从 `global.css` 推断）：
- 高度：约 64px（导航栏顶部区域）
- 宽度：自适应（建议 180-220px，与导航栏宽度匹配）
- 背景：深色导航栏（`#001529` 或类似）

**推荐尺寸**：
- **主 Logo**：180×64 像素（横向，适合导航栏）
- **紧凑版**：64×64 像素（正方形，适合折叠状态）
- **SVG 格式**：矢量格式，可缩放

**设计建议**：
- 深色导航栏：Logo 使用浅色（白色/浅灰）
- 考虑折叠状态：导航栏折叠时显示简化版图标
- 响应式：不同屏幕尺寸下清晰可见

---

## 二、Logo 文件结构

### 2.1 推荐的文件组织

```
frontend/
├── public/
│   ├── favicon.ico              # 浏览器标签页图标（多尺寸）
│   ├── favicon-16x16.png        # 16×16 像素
│   ├── favicon-32x32.png        # 32×32 像素
│   ├── apple-touch-icon.png     # 180×180 像素（iOS）
│   ├── logo.svg                 # 主 Logo（矢量，推荐）
│   ├── logo.png                 # 主 Logo（备用）
│   ├── logo-white.svg           # 白色版本（深色背景）
│   └── logo-compact.svg         # 紧凑版（折叠状态）
```

---

## 三、HTML 集成

### 3.1 更新 index.html

**文件位置**：`frontend/index.html`

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/x-icon" href="/favicon.ico" />
    <link rel="icon" type="image/png" sizes="16x16" href="/favicon-16x16.png" />
    <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png" />
    <link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png" />
    <title>YuLu 智链客服中台</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

---

## 四、React 组件集成

### 4.1 更新 PortalLayout.tsx

**文件位置**：`frontend/src/components/layout/PortalLayout.tsx`

```typescript
import { Layout, Menu, Dropdown, Avatar, Badge } from 'antd';
import { BellOutlined, LogoutOutlined } from '@ant-design/icons';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useMemo, useState } from 'react';
import { clearToken, getUsername } from '../../utils/storage';

// ... 其他代码 ...

export function PortalLayout({ children, logoText, headerTitle, menuItems }: PortalLayoutProps) {
  // ... 现有代码 ...

  return (
    <Layout style={{ height: '100vh' }}>
      <Sider
        theme="dark"
        width={220}
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        breakpoint="lg"
        collapsedWidth={64}
      >
        {/* Logo 区域 */}
        <div className="yulu-logo">
          {collapsed ? (
            // 折叠状态：显示简化图标
            <img 
              src="/logo-compact.svg" 
              alt="YuLu" 
              style={{ width: 32, height: 32, margin: '0 auto' }}
            />
          ) : (
            // 展开状态：显示完整 Logo
            <img 
              src="/logo-white.svg" 
              alt={logoText} 
              style={{ height: 40, width: 'auto' }}
            />
          )}
        </div>
        
        <Menu
          mode="inline"
          theme="dark"
          selectedKeys={selectedKey ? [selectedKey] : []}
          items={menuItems.map((it) => ({
            key: it.key,
            icon: it.icon,
            label: typeof it.label === 'string' ? <Link to={it.key}>{it.label}</Link> : it.label
          }))}
        />
      </Sider>
      {/* ... 其他代码 ... */}
    </Layout>
  );
}
```

---

## 五、样式优化

### 5.1 更新 global.css

**文件位置**：`frontend/src/styles/global.css`

```css
.yulu-logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(0, 0, 0, 0.2);
  transition: all 0.3s;
}

/* 折叠状态下的 Logo */
.ant-layout-sider-collapsed .yulu-logo {
  padding: 16px 8px;
}

/* Logo 图片样式 */
.yulu-logo img {
  display: block;
  max-width: 100%;
  height: auto;
  object-fit: contain;
}
```

---

## 六、Logo 设计建议

### 6.1 设计原则

1. **简洁明了**：
   - 小尺寸下清晰可辨
   - 避免过多细节

2. **品牌识别**：
   - 体现"YuLu"或"智链"品牌
   - 与客服/智能助手主题相关

3. **颜色适配**：
   - 深色导航栏：使用白色/浅色 Logo
   - 考虑不同背景的适配

4. **响应式设计**：
   - 提供多个尺寸版本
   - 考虑折叠/展开状态

### 6.2 设计元素建议

**可选元素**：
- 🔗 **链条图标**：体现"智链"概念
- 💬 **对话气泡**：体现客服/对话
- 🤖 **AI 元素**：体现智能化
- 📊 **数据/网络**：体现中台/平台

**配色建议**：
- 主色：蓝色系（科技感）
- 辅助色：白色/浅灰（导航栏背景）
- 强调色：橙色/绿色（可选，用于强调）

---

## 七、工具推荐

### 7.1 在线 Logo 生成工具

1. **Canva**：https://www.canva.com
   - 提供 Logo 模板
   - 支持导出多种尺寸

2. **LogoMaker**：https://www.logomaker.com
   - 专业 Logo 设计工具
   - 支持 SVG 导出

3. **Figma**：https://www.figma.com
   - 专业设计工具
   - 支持 SVG 导出

### 7.2 Favicon 生成工具

1. **Favicon.io**：https://favicon.io
   - 从图片生成多尺寸 Favicon
   - 支持 ICO、PNG 格式

2. **RealFaviconGenerator**：https://realfavicongenerator.net
   - 生成所有平台所需的图标
   - 自动生成 HTML 代码

---

## 八、尺寸规格总结

| 用途 | 尺寸 | 格式 | 位置 |
|------|------|------|------|
| **Favicon（标准）** | 32×32 | PNG/ICO | 浏览器标签页 |
| **Favicon（最小）** | 16×16 | PNG/ICO | 浏览器标签页 |
| **Favicon（高分辨率）** | 48×48 | PNG/ICO | 浏览器标签页 |
| **Apple Touch Icon** | 180×180 | PNG | iOS 设备 |
| **主 Logo（展开）** | 180×64 | SVG/PNG | 导航栏顶部 |
| **主 Logo（折叠）** | 64×64 | SVG/PNG | 导航栏顶部（折叠） |

---

## 九、实施步骤

### 步骤 1：设计 Logo
1. 使用设计工具创建 Logo
2. 导出 SVG 和 PNG 格式
3. 准备多个尺寸版本

### 步骤 2：生成 Favicon
1. 使用 Favicon 生成工具
2. 生成所有尺寸的图标
3. 保存到 `frontend/public/` 目录

### 步骤 3：更新代码
1. 更新 `index.html` 添加 favicon 链接
2. 更新 `PortalLayout.tsx` 使用 Logo 图片
3. 更新 `global.css` 优化样式

### 步骤 4：测试
1. 测试浏览器标签页图标显示
2. 测试导航栏 Logo 显示（展开/折叠）
3. 测试不同屏幕尺寸下的显示效果

---

## 十、示例代码（完整）

### 10.1 完整的 PortalLayout.tsx Logo 集成

```typescript
// Logo 组件
const Logo = ({ collapsed }: { collapsed: boolean }) => {
  if (collapsed) {
    return (
      <img 
        src="/logo-compact.svg" 
        alt="YuLu" 
        style={{ width: 32, height: 32 }}
      />
    );
  }
  return (
    <img 
      src="/logo-white.svg" 
      alt="YuLu 智链客服中台" 
      style={{ height: 40, width: 'auto', maxWidth: '100%' }}
    />
  );
};

// 在 PortalLayout 中使用
<Sider ...>
  <div className="yulu-logo">
    <Logo collapsed={collapsed} />
  </div>
  {/* ... 菜单 ... */}
</Sider>
```

---

## 十一、注意事项

1. **文件路径**：
   - 使用 `/logo.svg` 而不是 `./logo.svg`
   - Vite 会自动处理 `public/` 目录下的文件

2. **性能优化**：
   - 优先使用 SVG（矢量，文件小）
   - PNG 使用压缩工具优化

3. **浏览器兼容性**：
   - SVG 支持现代浏览器
   - 提供 PNG 作为备用

4. **可访问性**：
   - 添加 `alt` 属性
   - 确保 Logo 有足够的对比度

---

以上是完整的 Logo 设计规范和集成指南。按照这些规范设计 Logo 后，可以直接集成到项目中。


















