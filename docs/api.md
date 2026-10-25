# 一次买够 · 后端 API 接口文档

> 适用版本：schema v22（2026-10-21）
> 服务地址：`http://localhost:8080`（开发环境，前端经 Vite 代理 `/api` 访问）
> 本文覆盖 `src/main/java/com/tsuk1/itsmygo/controller/` 下全部接口。

---

## 1. 通用约定

### 1.1 请求格式

- 除文件上传外，请求与响应均为 `application/json; charset=UTF-8`。
- 时间字段为 ISO-8601 本地时间，如 `2026-10-20T19:03:01`。
- 金额字段为 `BigDecimal`，JSON 中是数字（如 `66.00`）。
- 文件上传使用 `multipart/form-data`，字段名统一为 `file`。

### 1.2 认证

登录/注册成功后返回 JWT，后续请求放在请求头：

```http
Authorization: Bearer <token>
```

角色只有三种：`CUSTOMER`、`MERCHANT`、`ADMIN`。
**权限以数据库为准**（`AuthIdentityService` 实时查询，60 秒缓存）；JWT 里的 role 不参与授权。
店铺不是 `ACTIVE` 时，商家角色会临时降为 `CUSTOMER`，旧 token 立即失去商家权限。

### 1.3 统一响应

```json
{
  "code": 200,
  "message": "成功",
  "data": {}
}
```

| code | 含义 | HTTP 状态 |
|---|---|---|
| 200 | 成功 | 200 |
| 400 | 参数错误 / 业务规则不满足 | **200** |
| 401 | 未登录、token 无效或已过期 | 401（Security 层）或 200（业务抛出） |
| 403 | 无权限 | 403（Security 层）或 200（业务抛出） |
| 404 | 资源不存在 / 越权访问 | **200** |
| 429 | 触发限流 | **200** |
| 500 | 服务器内部错误 | **200** |

> 注意：除了 Security 层直接返回的 401/403，其余业务错误 HTTP 状态仍是 200，
> 调用方必须判断响应体里的 `code`。

### 1.4 分页

```json
{
  "records": [],
  "total": 100,
  "page": 1,
  "size": 20
}
```

- `page` 从 1 开始。
- `size` 会被服务端夹在 1–100，防止一次拉穿数据库。
- 并不是所有列表都分页，见各接口说明。

### 1.5 枚举

**订单状态 `OrderStatus`**

| 值 | 含义 |
|---|---|
| `PENDING_PAYMENT` | 待支付 |
| `PAID` | 待发货 |
| `SHIPPED` | 待收货 |
| `COMPLETED` | 已完成 |
| `CANCELLED` | 已取消（终态） |
| `REFUNDING` | 退款中 |
| `REFUNDED` | 已退款（终态） |

**店铺状态 `Merchant.status`**

`PENDING` / `ACTIVE` / `REJECTED` / `DISABLED`

**用户券状态**

`UNUSED` / `USED` / `EXPIRED`

**聊天**

| 字段 | 取值 |
|---|---|
| `senderType` | `CUSTOMER` / `MERCHANT` / `AGENT`（买小购代发的平台客服消息） |
| `contentType` | `TEXT` / `IMAGE` / `PRODUCT` / `ORDER` |

### 1.6 权限标记

| 标记 | 含义 |
|---|---|
| 公开 | 无需登录 |
| 登录 | 任意已登录用户 |
| 顾客 | 已登录且不是 `ADMIN`（商家账号也可以作为顾客） |
| 商家 | `MERCHANT` 且店铺 `ACTIVE` |
| 管理员 | `ADMIN` |

---

## 2. 公共数据结构

### 2.1 认证与用户

**LoginResponse**

| 字段 | 类型 | 说明 |
|---|---|---|
| `token` | string | JWT |
| `userId` | number | 用户 ID |
| `username` | string | 用户名 |
| `nickname` | string | 昵称 |
| `avatar` | string | 头像 URL，空串表示未设置 |
| `role` | string | `CUSTOMER` / `MERCHANT` / `ADMIN` |
| `merchantId` | number/null | 商家账号有值 |
| `merchantName` | string | 店铺名 |
| `merchantStatus` | string | 店铺状态 |

**ProfileView**

`id, username, nickname, phone, avatar, role, merchantId, merchantName, merchantStatus`

`GET /api/auth/me` 返回的是 `CurrentUser`：`id, username, role, merchantId`。

### 2.2 地址

`Address`：`id, userId, receiver, phone, province, city, district, detail, isDefault(1/0), createdAt, updatedAt`

`AddressRequest`：

```json
{
  "receiver": "张三",
  "phone": "13800000000",
  "province": "广东省",
  "city": "深圳市",
  "district": "南山区",
  "detail": "科技园 1 号",
  "isDefault": true
}
```

### 2.3 分类

`Category`：`id, parentId, name, sort, status(1 启用/0 停用), createdAt, updatedAt`

`CategoryNode`：`id, parentId, name, sort, status, children[]`

`CategoryRequest`：`parentId`（null/0 表示顶层）、`name`、`sort`、`enabled`

### 2.4 商品与 SKU

**Product**

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 商品 ID |
| `merchantId` | number | 所属店铺 |
| `categoryId` | number | 分类 |
| `name` / `subtitle` | string | 名称 / 副标题 |
| `brand` | string | 品牌（品牌专区筛选依据，空串表示未归类） |
| `mainImage` | string | 主图 URL |
| `thumbImage` | string | 列表缩略图 URL；空串回落到 `mainImage` |
| `price` | number | 汇总价（在售 SKU 最低价） |
| `stock` | number | 汇总库存（在售 SKU 库存之和） |
| `sales` | number | 销量 |
| `hasSpecs` | number | 1 多规格 / 0 单规格 |
| `detail` | string | 富文本/详情 |
| `status` | number | 1 上架 / 0 下架 |
| `specs` | array/null | 仅详情返回：`ProductSpec[]` |
| `skus` | array/null | 仅详情返回：`ProductSku[]`（库存为 Redis 实时值） |

**ProductSpec**：`id, productId, name, sort, createdAt, updatedAt, values[]`
**ProductSpecValue**：`id, specId, value, sort, createdAt, updatedAt`
**ProductSku**：`id, productId, specValueIds(升序逗号串，空串=默认 SKU), specText, price, stock, sales, image, thumbImage, status(1 在售/0 停用), createdAt, updatedAt`
**ProductSkuView**：`{ specs: ProductSpec[], skus: ProductSku[] }`（SKU 库存为 Redis 实时值）
**BrandView**：`{ name: string, count: number }` —— 品牌名 + 当前在售商品数

**ProductRequest**（管理端/商家端共用）：

```json
{
  "categoryId": 1,
  "name": "商品名",
  "subtitle": "副标题",
  "mainImage": "https://...",
  "price": 66.00,
  "stock": 10,
  "detail": "详情",
  "onSale": true
}
```

**SkuSaveRequest**（整组覆盖）：

```json
{
  "specs": [
    { "id": null, "name": "颜色", "values": [{ "id": -1, "value": "黑色" }] }
  ],
  "skus": [
    { "specValueIds": [-1], "price": 66.00, "stock": 10, "image": "" }
  ]
}
```

- 已有规格/值传真实 id；新建规格传 `null`，新建规格值传**负数临时 id**。
- 已存在 SKU 的 `stock` 会被忽略；库存调整走单独接口。
- 无规格商品：`specs: []`、`skus: [{ "specValueIds": [], ... }]`。

**AdjustSkuStockRequest**：`{ "stock": 20, "expectedStock": 10 }`（CAS，原库存不一致则拒绝）

**StockView**：

```json
{
  "productId": 1,
  "stock": 10,
  "available": true,
  "skus": [{ "skuId": 2, "stock": 5, "available": true }]
}
```

### 2.5 购物车

**CartView**：`{ items: CartItemView[], selectedTotal: number }`

**CartItemView**：

`id, productId, skuId, productName, productImage, specText, price, stock, quantity, subtotal, selected, onSale, available, merchantId, merchantName, merchantActive`

- `selectedTotal` 只计算「已勾选且可购买」的行。
- `available=false` 表示商品/规格下架、店铺停用或库存不足。

### 2.6 订单

**Order**

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` / `orderNo` | number/string | 订单 ID / 订单号 |
| `userId` / `merchantId` | number | 买家 / 店铺 |
| `merchantName` | string | 店铺名快照（部分接口回填） |
| `totalAmount` | number | 实付 |
| `originalAmount` | number | 优惠前商品总额 |
| `discountAmount` | number | 优惠金额 |
| `userCouponId` / `couponName` | number/string | 使用的券及名称快照 |
| `status` | string | 见订单状态枚举 |
| `receiver` / `phone` / `address` | string | 收货信息快照 |
| `trackingNo` | string | 物流单号 |
| `statusBeforeRefund` | string/null | 退款前状态 |
| `refundReason` | string | 退款原因（≤200 字，未申请过为空串） |
| `refundAppliedAt` | datetime/null | 退款申请时间 |
| `refundHandledAt` | datetime/null | 退款处理（同意 / 驳回）时间 |
| `paidAt/shippedAt/completedAt/cancelledAt/createdAt/updatedAt` | datetime | 时间 |

**OrderItem**：`id, orderId, productId, skuId, productName, productImage, specText, unitPrice, quantity, subtotal, createdAt, reviewed(可选)`

**OrderView**：`{ order: Order, statusLabel: string, items: OrderItem[], itemCount: number }`

**CheckoutView**：

```json
{
  "orders": [OrderView],
  "order": Order,
  "statusLabel": "待支付",
  "items": [OrderItem],
  "itemCount": 2
}
```

- `orders` 是本次按店拆出的全部子订单。
- 顶层 `order/statusLabel/items/itemCount` 镜像第一个子订单，兼容旧调用方。

**CreateOrderRequest**：

```json
{
  "addressId": 1,
  "token": "一次性提交令牌",
  "items": [
    { "productId": 1, "skuId": 2, "quantity": 1 }
  ],
  "coupons": [
    { "merchantId": 1, "userCouponId": 10 }
  ]
}
```

- `items` 为空表示从购物车勾选行结算；非空表示「立即购买」，不碰购物车。
- `coupons`：每个店铺最多一张；同一张券不能出现在多个店铺；平台券可分配给任意一个子订单。

### 2.7 评价

**ReviewRequest**：`{ "orderItemId": 1, "rating": 5, "content": "很好" }`

- 仅已完成订单项可评；同一订单项只能评一次；`rating` 1–5。

**ReviewView**（公开评价）：`id, rating, content, userNickname, userAvatar, createdAt`
**ReviewSummary**：`{ averageRating: number, count: number }`
**MyReviewView**：`id, orderItemId, productId, productName, productImage, merchantId, merchantName, rating, content, status(1 展示/0 隐藏), createdAt`
**AdminReviewView**：`id, userId, userNickname, merchantId, merchantName, productId, productName, rating, content, status, createdAt`
**ReviewVisibleRequest**：`{ "visible": false }`

### 2.8 优惠券

**Coupon**（模板，管理/商家接口返回）：

`id, merchantId(NULL=平台券), name, thresholdAmount, discountAmount, totalCount, claimedCount, validFrom, validTo, status(1 启用/0 停用), createdAt, updatedAt`

**CouponView**（领券中心）：

`id, merchantId, merchantName, name, thresholdAmount, discountAmount, totalCount, claimedCount, remainingCount, validFrom, validTo, claimed`

**UserCouponView**（我的券）：

`id, couponId, merchantId, merchantName, couponName, thresholdAmount, discountAmount, validFrom, validTo, status, usedOrderId, createdAt`

**CouponRequest**：

```json
{
  "name": "满100减20",
  "thresholdAmount": 100.00,
  "discountAmount": 20.00,
  "totalCount": 100,
  "validFrom": "2026-10-01T00:00:00",
  "validTo": "2026-12-31T23:59:59",
  "enabled": true,
  "merchantId": null
}
```

- `merchantId=null` 建平台券；非空建店铺券（仅管理员可指定店铺）。
- 券一旦有领取记录，归属/金额/有效期不可修改。

### 2.9 商户与店铺

**MerchantView**：`id, name, logo, description, contactName, contactPhone, shipProvince, shipCity, shipDistrict, shipAddress, shipLng, shipLat, status, rejectReason, ownerUserId, createdAt`

- `GET /api/shops/{id}`（公开店铺）返回的 `ship*` 一律为空 / `null`，不暴露发货仓；
  只有商家自己（`/api/merchant/profile`）和平台后台能读到真实值。

**AdminMerchantView** = `MerchantView` + `ownerUsername`

**MerchantRequest**：

```json
{
  "name": "小张数码店",
  "logo": "https://...",
  "description": "店铺简介",
  "contactName": "张老板",
  "contactPhone": "13800000000"
}
```

- 店铺名全平台唯一；一个用户只能有一家店。
- 默认店铺 id=1「平台自营」由管理员处理消息，`ownerUserId=null`。

**MerchantRejectRequest**：`{ "reason": "资料不完整" }`

**ShipAddressRequest（发货地址）**：

```json
{
  "province": "浙江省",
  "city": "杭州市",
  "district": "西湖区",
  "address": "文三路 138 号",
  "lng": 120.11393,
  "lat": 30.27654
}
```

- 全部字段可空，传空即清空；`lng` / `lat` 必须成对，缺一视为未定位。
- 有坐标时虚拟物流从「店铺名 + 发货仓」出发；没有坐标时回退虚拟转运中心。
- 经度范围 `[-180, 180]`、纬度范围 `[-90, 90]`。

### 2.10 聊天

**ConversationView**：

| 字段 | 说明 |
|---|---|
| `id, merchantId, customerId` | 会话与双方 ID |
| `merchantName, merchantLogo` | 店铺信息 |
| `customerName, customerAvatar` | 顾客信息 |
| `lastMessageId, lastMessagePreview, lastMessageAt` | 最后一条消息 |
| `unread` | **当前身份**在这条会话里的未读数 |
| `peerOnline` | 顾客侧表示店铺是否在线（管理员连接算平台自营店在线） |
| `createdAt` | 会话创建时间 |

**ChatMessageView**：

`id, conversationId, senderUserId, senderType, contentType, content, card, createdAt`

- `TEXT`：`content` 是文本。
- `IMAGE`：`content` 是图片 URL（必须是系统 `chat/` 前缀）。
- `PRODUCT`：`card` 为商品快照。
- `ORDER`：`card` 为订单快照。

**商品卡片**

```json
{
  "productId": 1,
  "name": "商品名",
  "image": "https://...",
  "price": 66.00,
  "merchantId": 1,
  "status": 1
}
```

**订单卡片**

```json
{
  "orderId": 1,
  "orderNo": "202610201900001",
  "status": "PENDING_PAYMENT",
  "statusLabel": "待支付",
  "totalAmount": 66.00,
  "firstItemName": "商品名",
  "firstItemImage": "https://..."
}
```

**ChatMessagePage**：`{ records: ChatMessageView[], hasMore: boolean, peerLastReadMessageId: number/null }`

**ChatUnreadView**：`{ customerUnread: number, merchantUnread: number }`（全局未读总数）

**StartConversationRequest**：

```json
{ "merchantId": 1, "productId": 2, "orderId": null }
```

- `productId` / `orderId` 二选一，仅**新建会话**时自动发一张卡片。
- 平台自营店（id=1）允许发起，由管理员回复。

**SendChatMessageRequest**：

```json
{ "type": "TEXT", "text": "你好" }
{ "type": "IMAGE", "imageUrl": "http://localhost:9000/.../chat/x.png" }
{ "type": "PRODUCT", "productId": 1 }
{ "type": "ORDER", "orderId": 2 }
```

- `type` 决定使用哪个字段；卡片内容由服务端按 ID 查库生成，客户端不能伪造。
- 文本去空白后非空、≤2000 字。

**ChatReadRequest**：`{ "lastMessageId": 123 }`
**ChatStreamTicketView**：`{ "ticket": "uuid", "expiresInSeconds": 60 }`

---

## 3. 健康检查

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/health` | 公开 | 返回 `{status:"UP", time}` |

---

## 4. 认证

| 方法 | 路径 | 权限 | 请求体 | 返回 |
|---|---|---|---|---|
| POST | `/api/auth/register` | 公开 | `{username, password, nickname?}` | `LoginResponse` |
| POST | `/api/auth/login` | 公开 | `{username, password}` | `LoginResponse` |
| GET | `/api/auth/me` | 登录 | — | `CurrentUser` |

- 注册：用户名 3–32 位；密码 **8–64 位，且必须同时包含字母和数字**；昵称 ≤32；
  **注册出来的角色固定是 CUSTOMER**。
- 登录不重新校验密码强度，兼容历史账号（演示账号 `123456` 仍可登录）。
- 限流：注册 5 次/分钟/IP；登录 10 次/分钟/IP。
- 商家身份只能通过入驻审核获得，不能注册时指定。

---

## 5. 用户中心

| 方法 | 路径 | 权限 | 请求 | 返回 |
|---|---|---|---|---|
| GET | `/api/user/profile` | 登录 | — | `ProfileView` |
| PUT | `/api/user/profile` | 登录 | `{nickname, phone?}` | `ProfileView` |
| PUT | `/api/user/password` | 登录 | `{oldPassword, newPassword}` | `null` |
| POST | `/api/user/admin-invitation/accept` | 登录 | `{token}` | `null`（角色变为 ADMIN，需重新登录） |
| POST | `/api/user/avatar` | 登录 | multipart `file` | `ProfileView` |

- 改密码：`newPassword` 同样要求 **8–64 位且含字母和数字**；旧密码只要求正确，
  兼容历史弱密码。成功后服务端不吊销 JWT，前端应主动退出登录。
- 接受管理员邀请：要求当前登录用户就是邀请目标；邀请必须已由另一位管理员批准。
  成功后角色改为 ADMIN，旧 JWT 仍是 CUSTOMER，**必须重新登录**；token 一次性。
- 头像：jpg/jpeg/png/gif/webp，≤2MB，存 MinIO `avatars/`；限流 10 次/分钟/用户。

---

## 6. 收货地址

| 方法 | 路径 | 权限 | 请求 | 返回 |
|---|---|---|---|---|
| GET | `/api/addresses` | 登录 | — | `Address[]` |
| POST | `/api/addresses` | 登录 | `AddressRequest` | `Address` |
| PUT | `/api/addresses/{id}` | 登录 | `AddressRequest` | `Address` |
| DELETE | `/api/addresses/{id}` | 登录 | — | `null` |
| PUT | `/api/addresses/{id}/default` | 登录 | — | `null` |

- 只能操作自己的地址；越权/不存在统一 404。
- `isDefault=true` 时会把同用户其它地址的默认标记清掉；设默认接口幂等。

---

## 7. 分类（公开）

| 方法 | 路径 | 权限 | 参数 | 返回 |
|---|---|---|---|---|
| GET | `/api/categories` | 公开 | — | `CategoryNode[]`（树，仅启用分类） |

---

## 8. 商品（公开）

| 方法 | 路径 | 权限 | 参数 | 返回 |
|---|---|---|---|---|
| GET | `/api/products` | 公开 | `page=1, size=20, categoryId?, keyword?, brand?, merchantId?` | `PageResponse<Product>` |
| GET | `/api/products/brands` | 公开 | — | `BrandView[]`（品牌名 + 在售商品数，按数量降序） |
| GET | `/api/products/{id}` | 公开 | — | `Product`（含 specs/skus） |
| GET | `/api/products/{id}/stock` | 公开 | — | `StockView` |
| GET | `/api/products/{productId}/reviews` | 公开 | `page=1, size=5` | `PageResponse<ReviewView>` |
| GET | `/api/products/{productId}/reviews/summary` | 公开 | — | `ReviewSummary` |

- 只返回上架商品、且店铺为 `ACTIVE`。
- 详情命中缓存后仍会校验店铺状态；店铺停用后商品立即不可见。
- `keyword` 同时匹配商品名与副标题；`merchantId` 用于店铺主页。
- 库存接口读 Redis 实时计数，不查库。

---

## 9. 购物车

| 方法 | 路径 | 权限 | 请求 | 返回 |
|---|---|---|---|---|
| GET | `/api/cart` | 登录 | — | `CartView` |
| POST | `/api/cart/items` | 登录 | `{productId, skuId, quantity}` | `CartView` |
| PUT | `/api/cart/items/{id}` | 登录 | `{quantity, selected}` | `CartView` |
| DELETE | `/api/cart/items/{id}` | 登录 | — | `CartView` |

- 同一 `skuId` 重复加购会累加数量（数据库唯一键）。
- 加购会校验商品上架、SKU 在售、店铺 `ACTIVE`、库存充足。
- 购物车行不存店铺 ID，由商品派生；商品归属变化不会留下脏行。
- 所有写操作返回整份购物车，前端直接替换本地状态即可。

---

## 10. 订单、支付与售后

| 方法 | 路径 | 权限 | 参数/请求 | 返回 |
|---|---|---|---|---|
| GET | `/api/orders/token` | 登录 | — | `string`（一次性提交令牌） |
| POST | `/api/orders` | 登录 | `CreateOrderRequest` | `CheckoutView` |
| GET | `/api/orders` | 登录 | `page=1, size=10, previewLimit=3, status?` | `PageResponse<OrderView>` |
| GET | `/api/orders/{id}` | 登录 | — | `OrderView` |
| GET | `/api/orders/{id}/logistics` | 登录 | — | `LogisticsView \| null`（未发货为 null） |
| POST | `/api/orders/{id}/pay` | 登录 | — | `OrderView` |
| POST | `/api/orders/{id}/cancel` | 登录 | — | `OrderView` |
| POST | `/api/orders/{id}/confirm` | 登录 | — | `OrderView` |
| POST | `/api/orders/{id}/refund` | 登录 | `RefundRequest` | `OrderView` |

- 只能操作自己的订单，越权一律 404。
- 下单限流 10 次/分钟/用户。
- **跨店拆单**：一次下单按店铺生成多个子订单，各自支付、取消、确认收货、退款。
- `status` 支持逗号分隔的多个枚举，如 `PAID,SHIPPED`。
- `previewLimit=0` 返回全部明细。
- 支付是模拟支付：直接改状态为 `PAID` 并写支付流水。
- 取消会回滚库存；使用了未过期优惠券会自动退回。
- 申请退款要求状态为 `PAID` / `SHIPPED` / `COMPLETED`，进入 `REFUNDING` 等审核。
- `RefundRequest`：`{ "reason": "商品有瑕疵" }`，`reason` 最多 200 字；
  顾客端强制填写，后端允许为空（智能体代申请 / 老客户端）；
  申请后订单回显 `refundReason / refundAppliedAt`，审核后回显 `refundHandledAt`。
- 下单失败会回补 Redis 预扣库存；令牌一次性消费，防重复提交。

**LogisticsView（虚拟物流）**

```json
{
  "orderNo": "20261021204818419865",
  "trackingNo": "SFVIRTUAL002",
  "carrier": "极速物流",
  "courierName": "赵师傅",
  "courierPhone": "13800001233",
  "status": "IN_TRANSIT",
  "statusLabel": "运输中",
  "shippedAt": "2026-10-21 20:48:20",
  "eta": "2026-10-21 20:50:20",
  "deliveredAt": "",
  "progress": 42,
  "originName": "杭州转运中心",
  "originLng": 120.15507,
  "originLat": 30.274084,
  "destAddress": "浙江省杭州市西湖区文三路 1 号",
  "timeline": [
    { "status": "PICKED_UP", "label": "已揽收", "time": "2026-10-21 20:48:20", "done": true },
    { "status": "IN_TRANSIT", "label": "运输中", "time": "2026-10-21 20:48:50", "done": true },
    { "status": "OUT_FOR_DELIVERY", "label": "派送中", "time": "2026-10-21 20:49:56", "done": false },
    { "status": "DELIVERED", "label": "已送达", "time": "2026-10-21 20:50:20", "done": false }
  ]
}
```

- 只有订单所属用户可读；未发货返回 `data: null`。
- `eta` 就是最终送达时间；到点后 `status=DELIVERED`、`deliveredAt=eta`。
- `originName` 可能是「XX 转运中心」（未配发货地址）或「店铺名 + 发货仓」
  （商家 / 平台自营配置了发货坐标）。
- `destAddress` 只给前端腾讯地图做地理编码，**不进入 agent 上下文**。

---

## 11. 收藏

| 方法 | 路径 | 权限 | 参数 | 返回 |
|---|---|---|---|---|
| GET | `/api/favorites` | 登录 | `page=1, size=12` | `PageResponse<FavoriteView>` |
| GET | `/api/favorites/{productId}/status` | 登录 | — | `boolean` |
| POST | `/api/favorites/{productId}` | 登录 | — | `null` |
| DELETE | `/api/favorites/{productId}` | 登录 | — | `null` |

**FavoriteView**：`id, productId, name, subtitle, mainImage, price, status, createdAt, merchantId, merchantName`

- 收藏/取消均幂等；商品不存在返回 404。

---

## 12. 浏览足迹

| 方法 | 路径 | 权限 | 参数 | 返回 |
|---|---|---|---|---|
| POST | `/api/footprints/{productId}` | 登录 | — | `null` |
| GET | `/api/footprints` | 登录 | `page=1, size=12` | `PageResponse<FootprintView>` |
| DELETE | `/api/footprints` | 登录 | — | `null` |

**FootprintView**：`id, productId, name, subtitle, mainImage, price, status, viewedAt, merchantId, merchantName`

- 同一商品只保留一行，重复浏览只更新时间。
- 每个用户最多保留最近 100 条。

---

## 13. 评价

| 方法 | 路径 | 权限 | 请求/参数 | 返回 |
|---|---|---|---|---|
| POST | `/api/reviews` | 登录 | `ReviewRequest` | `null` |
| GET | `/api/reviews/mine` | 登录 | `page=1, size=10` | `PageResponse<MyReviewView>` |

- 提交限流 5 次/分钟/用户。
- 仅已完成订单项可评；同一订单项不可重复评价。
- 提交后立即展示；管理员/商家可隐藏，但不会删除。

---

## 14. 优惠券（顾客）

| 方法 | 路径 | 权限 | 参数/请求 | 返回 |
|---|---|---|---|---|
| GET | `/api/coupons` | 公开 | — | `CouponView[]` |
| POST | `/api/coupons/{couponId}/claim` | 登录 | — | `null` |
| GET | `/api/coupons/mine` | 登录 | `page=1, size=10, status?` | `PageResponse<UserCouponView>` |
| GET | `/api/coupons/mine/available` | 登录 | `amount, merchantId?` | `UserCouponView[]` |

- 领券中心匿名可看；登录后 `claimed` 标记有效。
- 店铺券只在店铺 `ACTIVE` 时展示/可领；平台券不受店铺影响。
- 重复领取 400；领完拒绝；过期券不展示。
- `available` 按金额筛选，并返回平台券 + 指定店铺的店铺券。
- 领取限流 10 次/分钟/用户。

---

## 15. 商户入驻与公开店铺

| 方法 | 路径 | 权限 | 请求/参数 | 返回 |
|---|---|---|---|---|
| POST | `/api/merchant-apply` | 登录 | `MerchantRequest` | `MerchantView` |
| GET | `/api/merchant-apply` | 登录 | — | `MerchantView` / `null` |
| POST | `/api/merchant-apply/logo` | 登录 | multipart `file` | `string`（URL） |
| GET | `/api/shops/{id}` | 公开 | — | `MerchantView` |

- 入驻：`PENDING` → 平台 `approve` → `ACTIVE`，用户角色变为 `MERCHANT`。
- 被驳回后可以修改资料重新提交；`PENDING`/`ACTIVE` 时不能重复申请。
- Logo 上传专门开放给申请人（此时还没有 MERCHANT 角色）：≤2MB，`merchants/` 前缀。
- 公开店铺接口只返回 `ACTIVE` 店铺。

---

## 16. 商家后台

> 以下接口均需要 `MERCHANT` 角色；`merchantId` 从登录身份推导，不信任请求参数。
> 跨店访问一律 404。

### 16.1 店铺资料

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| GET | `/api/merchant/profile` | — | `MerchantView` |
| PUT | `/api/merchant/profile` | `MerchantRequest` | `MerchantView` |
| PUT | `/api/merchant/ship-address` | `ShipAddressRequest` | `MerchantView` |

- `/ship-address` 只改当前商家的发货地址，不改店铺资料；平台自营店的发货地址由
  `/api/admin/merchants/{id}/ship-address` 代设。

### 16.2 商品与 SKU

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| GET | `/api/merchant/products` | `page=1, size=20, categoryId?, keyword?` | `PageResponse<Product>` |
| GET | `/api/merchant/products/{id}` | — | `Product` |
| POST | `/api/merchant/products` | `ProductRequest` | `Product` |
| PUT | `/api/merchant/products/{id}` | `ProductRequest` | `Product` |
| GET | `/api/merchant/products/{id}/skus` | — | `ProductSkuView` |
| PUT | `/api/merchant/products/{id}/skus` | `SkuSaveRequest` | `ProductSkuView` |
| PUT | `/api/merchant/products/{productId}/skus/{skuId}/stock` | `AdjustSkuStockRequest` | `ProductSku` |

- 商品归属不能跨店修改；只能看/改本店商品。
- SKU 保存是整组覆盖；已有 SKU 库存保持不变，补货走库存调整接口。
- 库存调整带 `expectedStock`，并发改过会拒绝。

### 16.3 订单

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| GET | `/api/merchant/orders` | `page=1, size=10, status?` | `PageResponse<Order>` |
| GET | `/api/merchant/orders/{id}` | — | `OrderView` |
| POST | `/api/merchant/orders/{id}/ship` | `{trackingNo}` | `OrderView` |
| POST | `/api/merchant/orders/{id}/refund/approve` | — | `OrderView` |
| POST | `/api/merchant/orders/{id}/refund/reject` | — | `OrderView` |

- 只能操作本店订单；别家订单 404。
- 发货要求订单状态为 `PAID`；退款审核要求 `REFUNDING`。

### 16.4 店铺券

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| GET | `/api/merchant/coupons` | `page=1, size=10, status?, keyword?` | `PageResponse<Coupon>` |
| POST | `/api/merchant/coupons` | `CouponRequest`（`merchantId` 会被强制为本店） | `Coupon` |
| PUT | `/api/merchant/coupons/{id}` | `CouponRequest` | `Coupon` |

### 16.5 评价

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| GET | `/api/merchant/reviews` | `page=1, size=10, visible?, keyword?` | `PageResponse<AdminReviewView>` |
| PUT | `/api/merchant/reviews/{id}/visible` | `{visible}` | `null` |

### 16.6 文件上传

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| POST | `/api/merchant/files/upload` | multipart `file` | `string`（URL） |

- 商品图：jpg/jpeg/png/gif/webp，≤5MB，`products/` 前缀。
- 限流 20 次/分钟/用户。

### 16.7 商家消息

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| GET | `/api/merchant/chat/conversations` | `page=1, size=30` | `PageResponse<ConversationView>` |
| GET | `/api/merchant/chat/conversations/{id}` | — | `ConversationView` |
| GET | `/api/merchant/chat/conversations/{id}/messages` | `beforeId?, afterId?, size=30` | `ChatMessagePage` |
| POST | `/api/merchant/chat/conversations/{id}/messages` | `SendChatMessageRequest` | `ChatMessageView` |
| POST | `/api/merchant/chat/conversations/{id}/read` | `{lastMessageId}` | `number`（全局未读） |
| GET | `/api/merchant/chat/unread-count` | — | `ChatUnreadView` |

- **没有商家创建会话的接口**：商家只能回复，不能主动私信。
- 发送限流 30 次/分钟/用户。

### 16.8 经营看板

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| GET | `/api/merchant/stats` | — | `MerchantDashboardView` |

- merchantId 只来自登录态，不能传参看别家数据。
- 今日/30 天 GMV 按 `paid_at` 且状态为 `PAID/SHIPPED/COMPLETED/REFUNDING`；
  订单数按 `created_at`；客单价 = 30 天 GMV / 已支付订单数。
- 待发货 = 当前 `PAID`，待退款 = 当前 `REFUNDING`；低库存 = 在售 SKU `stock <= 10`。
- 返回含 7 天趋势、热销 Top5、低库存前 6、最近 5 单；无新表。

---

## 17. 管理后台

> 以下接口均需要 `ADMIN` 角色。
> 默认店铺 id=1「平台自营」由管理员管理；平台消息也由管理员接收。

### 17.1 分类

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| GET | `/api/admin/categories` | — | `CategoryNode[]`（含停用） |
| POST | `/api/admin/categories` | `CategoryRequest` | `Category` |
| PUT | `/api/admin/categories/{id}` | `CategoryRequest` | `Category` |
| DELETE | `/api/admin/categories/{id}` | — | `null` |

- 有子分类或已绑定商品时不能删除。

### 17.2 商户审核

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| GET | `/api/admin/merchants` | `page=1, size=10, status?, keyword?` | `PageResponse<AdminMerchantView>` |
| GET | `/api/admin/merchants/{id}/operations` | — | `AdminMerchantOperationsView` |
| POST | `/api/admin/merchants/{id}/approve` | — | `null` |
| POST | `/api/admin/merchants/{id}/reject` | `{reason}` | `null` |
| POST | `/api/admin/merchants/{id}/disable` | — | `null` |
| POST | `/api/admin/merchants/{id}/enable` | — | `null` |
| PUT | `/api/admin/merchants/{id}/ship-address` | `ShipAddressRequest` | `MerchantView` |

- 只有 `PENDING` 能通过/驳回；默认店铺不能审核/禁用/转让。
- 审核/禁用后对应的授权缓存立即失效。
- `ship-address` 可用于所有商户，包括平台自营（id=1）；平台自营没有店主账号，
  只能从这里设置发货地址。
- `AdminMerchantView` 额外带 `gmv30 / orders30 / refunding30 / onSaleProducts /
  lowStockSkus`；`operations` 返回 30 天 KPI、近 7 天趋势、热销商品 Top 5 与最近 5 笔订单。

### 17.3 商品与 SKU

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| GET | `/api/admin/products` | `page=1, size=20, categoryId?, keyword?, merchantId?, status?, lowStock?` | `PageResponse<Product>` |
| POST | `/api/admin/products` | `ProductRequest` + query `merchantId=1` | `Product` |
| PUT | `/api/admin/products/{id}` | `ProductRequest` | `Product` |
| POST | `/api/admin/products/backfill-thumbnails` | — | number（补生成缩略图的商品/SKU 条数） |
| PUT | `/api/admin/products/batch-status` | `{ids:[...], onSale}` | `number`（更新数量，最多 100 个） |
| DELETE | `/api/admin/products/{id}` | — | `null` |
| GET | `/api/admin/products/{id}/skus` | — | `ProductSkuView` |
| PUT | `/api/admin/products/{id}/skus` | `SkuSaveRequest` | `ProductSkuView` |
| PUT | `/api/admin/products/{productId}/skus/{skuId}/stock` | `AdjustSkuStockRequest` | `ProductSku` |
| POST | `/api/admin/products/rebuild-bloom` | — | `number`（重建数量） |

- 管理端可查看/管理全部店铺商品，并可按 `merchantId` 筛选。
- `status`：1 上架 / 0 下架；`lowStock=true` 只返回有 SKU `stock <= 10` 的商品。
- 批量上下架只改商品状态，更新后清商品详情缓存；单个商品仍可走 `PUT /{id}`。
- 新建商品默认归属平台自营（merchantId=1），也可显式指定。
- `rebuild-bloom` 用于修复手工 SQL/导入脚本写入、未进入布隆过滤器的商品。

### 17.4 订单

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| GET | `/api/admin/orders` | `page=1, size=10, status?, merchantId?, keyword?, dateFrom?, dateTo?` | `PageResponse<Order>` |
| GET | `/api/admin/orders/export` | 同上（不分页，最多 5000 条） | CSV 文件 |
| GET | `/api/admin/orders/{id}` | — | `OrderView` |
| POST | `/api/admin/orders/{id}/ship` | `{trackingNo}` | `OrderView` |
| POST | `/api/admin/orders/{id}/refund/approve` | — | `OrderView` |
| POST | `/api/admin/orders/{id}/refund/reject` | — | `OrderView` |
| POST | `/api/admin/orders/cancel-expired` | `minutes=30` | `number`（取消数量） |

- 平台保留全平台订单的查看、发货、退款审核权限。
- 超时未支付订单会由 RabbitMQ 延迟消息自动取消（默认 30 分钟），数据库扫描兜底；
  `cancel-expired` 仍保留，用于手动清理存量 / 调整阈值。
- `keyword` 同时匹配订单号 / 收货人 / 电话 / 运单号；`dateFrom` / `dateTo` 为
  `YYYY-MM-DD`，按下单时间过滤（含首尾两天）。
- `export` 返回 `text/csv;charset=UTF-8`，带 UTF-8 BOM（Excel 中文不乱码），
  列：订单号、下单时间、支付时间、状态、店铺、用户 ID、收货人、电话、金额、
  优惠、物流单号、退款原因、退款申请时间、退款处理时间。
- `cancel-expired` 当前是手动触发；传 `minutes` 表示取消创建超过该分钟数且未支付的订单。

### 17.5 优惠券

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| GET | `/api/admin/coupons` | `page=1, size=10, enabled?, keyword?, merchantId?` | `PageResponse<Coupon>` |
| POST | `/api/admin/coupons` | `CouponRequest` | `Coupon` |
| PUT | `/api/admin/coupons/{id}` | `CouponRequest` | `Coupon` |

- 可建平台券（`merchantId=null`）或指定店铺券。
- 不提供删除；停用后不再可领，已领的券仍可用。

### 17.6 评价

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| GET | `/api/admin/reviews` | `page=1, size=10, visible?, keyword?, merchantId?` | `PageResponse<AdminReviewView>` |
| PUT | `/api/admin/reviews/{id}/visible` | `{visible}` | `null` |

### 17.7 文件上传

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| POST | `/api/admin/files/upload` | multipart `file` | `string`（URL） |

- 商品图，规则同商家上传。

### 17.8 平台消息（平台自营店）

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| GET | `/api/admin/chat/conversations` | `page=1, size=30` | `PageResponse<ConversationView>` |
| GET | `/api/admin/chat/conversations/{id}` | — | `ConversationView` |
| GET | `/api/admin/chat/conversations/{id}/messages` | `beforeId?, afterId?, size=30` | `ChatMessagePage` |
| POST | `/api/admin/chat/conversations/{id}/messages` | `SendChatMessageRequest` | `ChatMessageView` |
| POST | `/api/admin/chat/conversations/{id}/read` | `{lastMessageId}` | `number`（全局未读） |
| GET | `/api/admin/chat/unread-count` | — | `ChatUnreadView` |

- 只暴露 `merchant_id=1` 的会话；普通商家会话一律 404。
- 所有正常状态的管理员共同接收平台自营店消息；管理员 SSE 连接算平台自营店在线。
- 管理员不能调用顾客侧的**会话/消息接口**（`/api/chat/conversations/**`）；
  `unread-count`、`stream-ticket`、`stream`、`files/upload` 仍可正常使用。

### 17.9 数据概览

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| GET | `/api/admin/dashboard` | — | `AdminDashboardView` |

- `AdminDashboardView`：`todayGmv / todayOrders / todayPaidOrders /
  pendingShipments / pendingRefunds / pendingMerchants / lowStockSkus /
  totalUsers / activeMerchants / onSaleProducts / trend / pendingShipmentOrders /
  pendingRefundOrders / pendingMerchantList / lowStockList`。
- 今日成交额按 `paid_at` 当天、状态 `PAID / SHIPPED / COMPLETED / REFUNDING` 求和；
  低库存阈值为 `stock <= 10`；待办列表各取最新 5 条。
- `trend` 固定返回最近 7 天，没有成交的日期补 0。

### 17.10 用户管理

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| GET | `/api/admin/users` | `page=1, size=10, keyword?, role?, status?` | `PageResponse<AdminUserView>` |
| POST | `/api/admin/users/{id}/disable` | — | `Result<Void>` |
| POST | `/api/admin/users/{id}/enable` | — | `Result<Void>` |

- `keyword` 模糊匹配用户名 / 昵称 / 手机号；`role` 取 CUSTOMER/MERCHANT/ADMIN；
  `status` 取 1 正常 / 0 禁用。
- `AdminUserView` 不含密码：`id/username/nickname/phone/avatar/status/role/merchantId/
  createdAt/updatedAt`。
- 不能禁用当前登录账号；不能禁用最后一个活跃管理员。
- 禁用后旧 JWT 在下一次请求时失效（AuthIdentityService 缓存立即 evict），
  登录接口也会拒绝已禁用账号。

### 17.11 操作审计

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| GET | `/api/admin/audit-logs` | `page=1, size=10, keyword?, action?, result?, dateFrom?, dateTo?` | `PageResponse<AdminAuditView>` |

- 只读接口，不提供新增 / 修改 / 删除。
- `keyword` 匹配操作人 / 摘要 / 目标 ID / traceId / IP；`result` 取 SUCCESS/FAILED；
  日期按创建时间（`created_at`）过滤，含首尾两天。
- `AdminAuditView`：`id/actorUserId/actorUsername/action/targetType/targetId/summary/
  result/errorMessage/ip/userAgent/traceId/detail/createdAt`。
- 管理端写操作由 AOP 记录；`@Valid` 参数校验失败、401/403 不进入审计。

### 17.12 知识库导入

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| POST | `/api/admin/knowledge/upload` | multipart `files`（PDF/Markdown，可多个）+ `force=false` | `KnowledgeUploadResult` |
| GET | `/api/admin/knowledge/documents` | — | `KnowledgeDocumentList` |
| GET | `/api/admin/knowledge/tasks/{taskId}` | — | `KnowledgeTaskStatus` |
| DELETE | `/api/admin/knowledge/documents/{fileTitle}` | — | `null` |

- 仅 ADMIN；Java 带 `AGENT_IMPORT_KEY`（默认复用 `AGENT_GRPC_KEY`）代理到
  rag_helper 的 8001，浏览器不直连 agent。
- `KnowledgeUploadResult`：`message, taskIds[], duplicates[], failed[]`；
  `duplicates[]` 含 `filename/fileTitle/itemName/chunkCount/status/importedAt`，
  `failed[]` 含 `filename/reason`。
- `KnowledgeTaskStatus`：`taskId/status/doneList/runningList`，阶段名已是中文；
  前端轮询直到 `completed` / `failed`。
- `KnowledgeDocumentList`：`total, documents[]`；
  文档字段 `fileTitle/itemName/chunkCount/importedAt/status/kgEntities`。
- 扩展名在 Java 侧先校验；单文件上限 200MB（multipart 全局上限），
  商品图上传仍在 `StorageService` 单独限制 5MB。
- 上传 / 撤回写 `admin_audit_logs`（`KNOWLEDGE_UPLOAD` / `KNOWLEDGE_REVOKE`）；
  上传限流 5 次/60s/用户。撤回不可恢复。

### 17.13 管理员邀请

| 方法 | 路径 | 请求/参数 | 返回 |
|---|---|---|---|
| POST | `/api/admin/invitations` | `{username}` | `AdminInvitationView`（**含一次性 token**） |
| GET | `/api/admin/invitations` | `status?` | `AdminInvitationView[]`（不含 token） |
| POST | `/api/admin/invitations/{id}/approve` | — | `AdminInvitationView` |
| POST | `/api/admin/invitations/{id}/revoke` | — | `AdminInvitationView` |

- 仅 ADMIN；只能邀请角色为 `CUSTOMER` 且状态正常的账号。
- 发起人**不能批准自己的邀请**，必须由另一位管理员批准。
- 状态：`PENDING_APPROVAL → APPROVED → ACCEPTED`，另有 `REVOKED` / `EXPIRED`。
- token 只存 SHA-256，明文只在创建响应出现一次；有效期默认 24 小时
  （`ADMIN_INVITE_EXPIRY_HOURS`）。
- 发起 / 批准 / 撤回写 `admin_audit_logs`；接受由用户接口写入 `ADMIN_INVITE_ACCEPT`。

**AdminInvitationView**：`id/targetUserId/targetUsername/targetNickname/
invitedBy/inviterUsername/approvedBy/approverUsername/status/expiresAt/approvedAt/
acceptedAt/createdAt`，创建响应额外带 `token`。

---

## 18. 顾客聊天

| 方法 | 路径 | 权限 | 请求/参数 | 返回 |
|---|---|---|---|---|
| POST | `/api/chat/conversations` | 顾客 | `StartConversationRequest` | `ConversationView` |
| GET | `/api/chat/conversations` | 顾客 | `page=1, size=30` | `PageResponse<ConversationView>` |
| GET | `/api/chat/conversations/{id}` | 顾客 | — | `ConversationView` |
| GET | `/api/chat/conversations/{id}/messages` | 顾客 | `beforeId?, afterId?, size=30` | `ChatMessagePage` |
| POST | `/api/chat/conversations/{id}/messages` | 顾客 | `SendChatMessageRequest` | `ChatMessageView` |
| POST | `/api/chat/conversations/{id}/read` | 顾客 | `{lastMessageId}` | `number`（全局未读） |
| GET | `/api/chat/unread-count` | 登录 | — | `ChatUnreadView` |
| POST | `/api/chat/stream-ticket` | 登录 | — | `ChatStreamTicketView` |
| GET | `/api/chat/stream` | 票据 | query `ticket` | `text/event-stream` |
| POST | `/api/chat/files/upload` | 登录 | multipart `file` | `string`（URL） |

规则与约束：

- 每家店 × 每个顾客只有一条会话，重复发起返回同一条。
- 新建会话可带 `productId` 或 `orderId`，自动发一张对应卡片；重复点「联系卖家」不会重复发。
- 卡片归属校验：商品/订单必须属于该店铺；订单还必须属于该顾客。
- 商家离线仍可发送（留言），界面显示离线状态。
- 店铺停用后：可读历史，发送返回 400。
- 图片必须来自本系统 `chat/` 前缀，外链 400；图片 ≤5MB。
- 房间参与者以外的人访问会话一律 404。
- 限流：创建会话 10 次/分钟、发消息 30 次/分钟、票据 20 次/分钟、图片上传 20 次/分钟。

### 18.1 SSE 连接流程

1. `POST /api/chat/stream-ticket`（带 JWT）拿到 `ticket`，有效期 60 秒，**一次性消费**。
2. `GET /api/chat/stream?ticket=...` 建立 SSE。EventSource 不能带 Authorization，所以用票据。
3. 前端断线后必须 `close()`、重新取票、指数退避重连；旧的 ticket 已失效。
4. 服务端每 25 秒发一次注释心跳 `: ping`。

连接建立后立即收到：

```text
event: connected
data: {"userId":1,"customerUnread":0,"merchantUnread":0}
```

### 18.2 SSE 事件

| event | data | 说明 |
|---|---|---|
| `connected` | `{userId, customerUnread, merchantUnread}` | 连接建立，带全局未读 |
| `message` | `{scope, message, conversation, unread}` | 新消息；`scope` 为接收方身份，`unread` 为接收方全局未读 |
| `read` | `{conversationId, readerType, lastReadMessageId}` | 对端已读回执 |
| `presence` | `{merchantId, online}` | 店铺在线状态变化；管理员连接算平台自营店在线 |

> SSE 连接表存在单实例内存中；多实例部署需要 Redis Pub/Sub 或粘性会话。

---

## 19. 客服智能体「买小购」

> Java 经 gRPC 调用 rag_helper 的 Agent 服务（内网 8003）；浏览器只跟 Java 说话。
> 客服对话使用 **POST + SSE 流式响应**，不是 EventSource ——
> 每次对话的事件流只属于这次调用，POST 可以直接带 Authorization。

### 19.1 接口

| 方法 | 路径 | 权限 | 请求/参数 | 返回 |
|---|---|---|---|---|
| POST | `/api/agent/converse` | 登录 | `{sessionId?, message}` | `text/event-stream` |
| POST | `/api/agent/stop` | 登录 | `{sessionId, runId}` | `boolean` |
| GET | `/api/agent/history` | 登录 | `sessionId, limit=20` | `AgentHistoryView` |
| GET | `/api/agent/sessions` | 登录 | `limit=20` | `AgentSessionView[]` |

- `sessionId` 为空表示新会话；Java 生成 UUID 并记录归属，在 `start` 事件里返回。
- `message` 去空白后非空、≤2000 字。
- 会话归属在 Java 侧校验：Stop / History / 带 sessionId 的 Converse，
  只要 `session_id + user_id` 不匹配就返回 404。
- `/api/agent/sessions` 返回当前用户的历史会话（跨设备可用），按最近活跃倒序；
  agent 与 Java 两层都按用户过滤，**不会返回别人的会话**。只有产生过消息的会话才会出现。
- 同一会话正在回复时再次发起对话，返回 429。
- 限流：Conversation 10 次/分钟/用户。
- Agent 服务不可用时，应用照常运行，对话流里返回 `AGENT_UNAVAILABLE`。

**AgentSessionView**

```json
{
  "sessionId": "64fef1ae81ef4f8fa91b6a4807cf885e",
  "title": "联想拯救者r9000p进水了怎么办？",
  "count": 4,
  "lastAt": "2026-10-20T12:47:05+00:00"
}
```

- `title`：该会话第一条 user 消息（超 30 字截断）。
- `lastAt`：最近活跃时间（agent 侧 ObjectId 时间，UTC）。

**AgentHistoryView**

```json
{
  "messages": [
    {
      "role": "user",
      "text": "打印机怎么装墨盒？",
      "images": [],
      "ts": "2026-10-20 20:23:29",
      "traces": []
    },
    {
      "role": "assistant",
      "text": "……",
      "images": [{ "url": "https://...", "caption": "图注" }],
      "ts": "2026-10-20 20:23:32",
      "traces": [
        { "kind": "delegate", "agent": "product-kb", "detail": "检索产品手册" },
        { "kind": "reply", "agent": "", "detail": "生成回复（N 字符）" }
      ]
    }
  ]
}
```

- `traces` 是该轮执行流；老消息没有 trace，返回空数组。
- `products` 是该轮导购推荐的商品卡；老消息没有 products，返回空数组。
  前端历史消息默认折叠成一行「执行过程 · N 步」，点击展开全部步骤。

### 19.2 Converse SSE 事件

`POST /api/agent/converse` 的响应为 `text/event-stream`，帧格式与普通 SSE 一致：

```text
event: trace
data: {"kind":"delegate","agent":"product-kb","detail":"检索产品手册"}
```

| event | data |
|---|---|
| `start` | `{sessionId, runId}` |
| `trace` | `{kind, agent, detail}`（`kind`：delegate/tool/result/reply/node） |
| `delta` | `{delta}`（一期通常不出现，前端仍兼容） |
| `usage` | `{calls, failed, totalTokens, cost, elapsedMs, unpricedCalls}` |
| `final` | `{answer, images:[{url,caption}], products:[ProductCard], webOnly}` |
| `error` | `{code, message, retryable}` |
| `done` | `{reason: "final" / "stopped" / "error"}` |

错误码：

| code | 含义 | 可重试 |
|---|---|---|
| `AGENT_UNAVAILABLE` | 客服服务不可用 | 是 |
| `AGENT_TIMEOUT` | 响应超时 | 是 |
| `AGENT_AUTH_FAILED` | 客服服务鉴权失败 | 否 |
| `AGENT_BUSY` | 客服繁忙 | 是 |
| `AGENT_ERROR` | 其它客服错误 | 是 |

说明：

- 服务端每 20 秒发一次 SSE comment 心跳（`: ping`）。
- 收到 `final` 后仍可能继续收到 `usage`，最后以 `done` 结束。
- 用户点停止 / 客户端断开时，Java 会用 `runId` 调 gRPC `Stop`。
- 该流**不自动重连、不自动重试**；失败由用户重新发送。
- 反代（Nginx）需 `proxy_buffering off; proxy_read_timeout 300s;`。

### 19.3 内部工具接口（agent 专用，非浏览器接口）

agent 服务在对话过程中需要实时数据时，会反向回调 Java 的内部接口。
**只在内网使用，浏览器不调用**；路径由 `SecurityConfig` 放行，鉴权完全由
每轮对话签发的票据承担。

| 方法 | 路径 | 鉴权 | 请求 | 返回 |
|---|---|---|---|---|
| POST | `/internal/agent/orders/query` | `X-Agent-Ticket` | `{orderNo?, status?, limit?}` | `AgentOrderView[]` |
| POST | `/internal/agent/catalog/products` | `X-Agent-Ticket` | `{keyword?, categoryId?, priceMin?, priceMax?, sort?, limit?}` | `AgentProductView[]` |
| GET | `/internal/agent/catalog/all` | `X-Agent-Ticket` | `limit=100` | `AgentProductView[]` |
| GET | `/internal/agent/catalog/categories` | `X-Agent-Ticket` | — | `CategoryNode[]` |
| GET | `/internal/agent/catalog/addresses` | `X-Agent-Ticket` | — | `AgentAddressView[]` |
| POST | `/internal/agent/actions/order` | `X-Agent-Ticket` | `{addressId?, items:[{productId,skuId,quantity}]}` | `AgentActionView` |
| POST | `/internal/agent/actions/refund` | `X-Agent-Ticket` | `{orderNo}` | `AgentActionView` |
| POST | `/internal/agent/actions/urge-shipment` | `X-Agent-Ticket` | `{orderNo}` | `AgentActionView` |
| POST | `/internal/agent/actions/urge-shipment/send` | `X-Agent-Ticket` | `{actionNo}` | `AgentActionView` |
| POST | `/internal/agent/actions/urge-refund` | `X-Agent-Ticket` | `{orderNo}` | `AgentActionView` |
| POST | `/internal/agent/actions/urge-refund/send` | `X-Agent-Ticket` | `{actionNo}` | `AgentActionView` |

- 商品搜索：`sort` 取 `recommend`（默认，有货优先 + 销量/评分）/ `sales` /
  `price_asc` / `price_desc` / `newest`；`limit` 默认 5、上限 10。
- `AgentProductView` 带 `price / totalStock / sales / rating / reviewCount /
  merchantName / categoryName / skus`；`/catalog/all` 是语义索引的全量入口（上限 100）。
- 商品目录**不做 text2sql**：模型只选筛选参数，SQL 由 Java 固定生成。

- 商品搜索返回商品与 SKU（skuId / specText / price / stock），供 agent 落到具体规格。
- 地址只返回当前票据用户自己的；动作准备接口只落 `PENDING_CONFIRM` 草稿，**不执行**。
- 确认必须走 §19.4 的浏览器接口，agent 不能确认。
- 催发货确认后，Java 用内部事件触发 agent；agent 用本轮票据回调
  `/actions/urge-shipment/send`，Java 校验动作归属 / `EXECUTING` 状态 / 订单后，
  才以 `senderType=AGENT` 落库。该接口只给 agent 用，浏览器不能直接调用。
- 催退款只允许 `REFUNDING` 订单；确认后内部事件为 `urge-refund-confirmed`，
  agent 回调 `/actions/urge-refund/send`。Java 会重新校验订单仍为退款中，
  已退款 / 已驳回返回 400，不写商家消息。

- 票据由 Java 在每次 `POST /api/agent/converse` 时签发：
  Redis `its-my-go:agent:tool-ticket:{uuid}`，TTL 10 分钟；
  经 gRPC metadata `x-agent-tool-ticket` 下发给 agent，**不进模型上下文**。
- 请求体**没有 userId**：Java 从票据解析用户，模型无法指定他人。
- `orderNo` 为空时返回最近订单；`status` 逗号分隔；`limit` 默认 5、上限 10。
- 别人的订单返回空数组（不泄露存在性）。
- 响应**不含**收货人、电话、地址，只给订单号、状态、店铺、金额、物流单号、时间与商品行。
- 票据缺失 / 过期 → `{"code":401,"message":"工具票据无效或已过期"}`。

**AgentOrderView**

```json
{
  "orderNo": "20261019151614788725",
  "status": "SHIPPED",
  "statusLabel": "待收货",
  "merchantName": "商家A数码店",
  "totalAmount": "66.00",
  "trackingNo": "SFMERCHANTA001",
  "createdAt": "2026-10-19 15:16:14",
  "paidAt": "2026-10-19 15:16:30",
  "shippedAt": "2026-10-19 15:17:02",
  "logistics": {
    "status": "IN_TRANSIT",
    "statusLabel": "运输中",
    "eta": "2026-10-21 20:50:20",
    "carrier": "极速物流",
    "courierName": "赵师傅",
    "progress": 42
  },
  "items": [
    { "productName": "商家A测试商品", "specText": "颜色:黑色", "quantity": 1, "unitPrice": "66.00" }
  ]
}
```

**ProductCard**（final / 历史里的导购商品卡）

```json
{
  "productId": "1655",
  "name": "兄弟 HAK 180 烫金机",
  "image": "",
  "price": "1999.00",
  "stock": "4",
  "sales": "92",
  "rating": "",
  "merchantName": "平台自营",
  "specText": "",
  "categoryName": "办公设备"
}
```

- 最终回复里的 `products` 只保留回复实际引用到的商品（`/product/{id}` 或商品名）；
  历史消息同样返回该字段。

### 19.4 待确认动作（浏览器确认接口）

| 方法 | 路径 | 权限 | 请求 / 参数 | 返回 |
|---|---|---|---|---|
| GET | `/api/agent/actions` | 登录 | `sessionId` | `AgentActionView[]` |
| POST | `/api/agent/actions/{actionNo}/confirm` | 登录 | — | `AgentActionView` |
| POST | `/api/agent/actions/{actionNo}/cancel` | 登录 | — | `AgentActionView` |

- 确认接口**只收 actionNo**，业务参数全部来自服务端快照；客户端无法在确认时
  偷改数量 / 地址 / 金额。
- 只有动作所属用户能确认 / 取消；别人的动作一律 404。
- 状态：`PENDING_CONFIRM / EXECUTING / SUCCEEDED / FAILED / CANCELLED / EXPIRED`。
- 列表接口返回前会按订单当前状态覆盖 `result` 里的 `status / statusLabel`；
  支付、发货、退款后卡片会自动更新，不依赖动作执行时的快照。
- 有效期默认 600 秒（`AGENT_ACTION_EXPIRY_SECONDS`）；过期后确认返回 EXPIRED，不执行。
- 双击确认幂等：条件更新只有一个请求会进入 EXECUTING。
- 限流：confirm / cancel 10 次/60s/用户；总开关 `AGENT_ACTIONS_ENABLED=false`。
- 下单成功后的 `result.orders[]` 带 `id / orderNo / status / statusLabel / totalAmount`，
  前端据此给「去支付」入口；支付仍走 `POST /api/orders/{id}/pay`。
- `CREATE_ORDER` 成功后 Java 会触发 agent 内部事件 `order-confirmed`，agent 写入一条
  assistant 历史（订单号 / 应付金额；不写易变状态，状态以订单详情页为准）；
  该通知失败不影响订单创建。
- 智能下单暂不支持优惠券。

**AgentActionView**

```json
{
  "actionNo": "8f6f...",
  "type": "CREATE_ORDER",
  "status": "PENDING_CONFIRM",
  "summary": {
    "address": { "id": 1582, "receiver": "李雷", "phone": "139...", "address": "北京市..." },
    "groups": [
      {
        "merchantId": 1,
        "merchantName": "平台自营",
        "items": [
          { "productName": "兄弟 HAK 180 烫金机", "specText": "", "quantity": 1,
            "unitPrice": "1999.00", "subtotal": "1999.00", "image": "" }
        ],
        "subtotal": "1999.00",
        "discount": "0.00",
        "total": "1999.00"
      }
    ],
    "itemCount": 1,
    "originalAmount": "1999.00",
    "discountAmount": "0.00",
    "totalAmount": "1999.00",
    "note": "确认后生成待支付订单，支付请在订单详情页完成；智能下单暂不支持优惠券。"
  },
  "result": null,
  "errorMessage": "",
  "expiresAt": "2026-10-21T13:17:00",
  "createdAt": "2026-10-21T13:07:00"
}
```

退款动作的 `summary` 是 `{orderNo, merchantName, status, statusLabel, totalAmount, items, note}`；
成功后的 `result` 是 `{orderId, orderNo, status, statusLabel, note}`。

催发货动作（`type=URGE_SHIPMENT`）的 `summary` 是
`{orderNo, merchantName, status, statusLabel, totalAmount, itemCount, items, note}`；
成功后的 `result` 是
`{orderId, orderNo, merchantName, status, statusLabel, messageId, note}`。
只有待发货（`PAID`）且未发货的订单能催；成功发送后 10 分钟内不能重复催同一单；
确认时会重新校验状态，商家已发货（待收货）则动作失败且不发送消息。
商家会话收到的是 `senderType=AGENT` 的文本消息；同时 agent 会话会写入一条
assistant 完成回复（「催发货已完成 + 代商家致歉」）。

催退款动作（`type=URGE_REFUND`）的 `summary` 是
`{orderNo, merchantName, status, statusLabel, totalAmount, refundReason,
refundAppliedAt, itemCount, items, note}`；成功后的 `result` 是
`{orderId, orderNo, merchantName, status, statusLabel, messageId,
refundReason, refundAppliedAt, note}`。
只有退款中（`REFUNDING`）的订单能催；已退款（`REFUNDED`）返回
「商家已处理退款，无需再催」；确认时重新校验，商家已处理 / 驳回则动作失败且不发送消息。
成功发送后 10 分钟内不能重复催同一单，agent 随后写一条「催退款已完成 + 代商家致歉」。

---

## 20. 限流汇总

| 场景 | 接口 | 限制 | 维度 |
|---|---|---|---|
| 注册 | `POST /api/auth/register` | 5 次/60s | IP |
| 登录 | `POST /api/auth/login` | 10 次/60s | IP |
| 头像上传 | `POST /api/user/avatar` | 10 次/60s | 用户 |
| 下单 | `POST /api/orders` | 10 次/60s | 用户 |
| 提交评价 | `POST /api/reviews` | 5 次/60s | 用户 |
| 领券 | `POST /api/coupons/{id}/claim` | 10 次/60s | 用户 |
| 通用上传 | admin/merchant/merchant-apply 上传 | 20 次/60s | 用户 |
| 创建会话 | `POST /api/chat/conversations` | 10 次/60s | 用户 |
| 发聊天消息 | `POST .../messages` | 30 次/60s | 用户 |
| SSE 票据 | `POST /api/chat/stream-ticket` | 20 次/60s | 用户 |
| 聊天图片 | `POST /api/chat/files/upload` | 20 次/60s | 用户 |
| 客服对话 | `POST /api/agent/converse` | 10 次/60s | 用户 |
| 智能动作确认 / 取消 | `POST /api/agent/actions/{actionNo}/confirm\|cancel` | 10 次/60s | 用户 |

触发限流返回 `{"code":429,"message":"请求过于频繁，请稍后再试","data":null}`（HTTP 200）。

---

## 21. 上传约束

| 用途 | 接口 | 大小 | 存储前缀 | 允许格式 |
|---|---|---|---|---|
| 商品图 | `/api/admin/files/upload`、`/api/merchant/files/upload` | ≤5MB | `products/` | jpg/jpeg/png/gif/webp |
| 头像 | `/api/user/avatar` | ≤2MB | `avatars/` | 同上 |
| 店铺 Logo | `/api/merchant-apply/logo`、商家资料上传 | ≤2MB | `merchants/` | 同上 |
| 聊天图片 | `/api/chat/files/upload` | ≤5MB | `chat/` | 同上 |

- 上传成功返回可直接访问的 MinIO 公网 URL。
- 聊天消息只接受 `chat/` 前缀的图片 URL，拒绝外部链接。

---

## 22. 典型调用示例

**登录**

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"123456"}'
```

**带 token 查询我的订单**

```bash
curl "http://localhost:8080/api/orders?page=1&size=10" \
  -H "Authorization: Bearer <token>"
```

**从商品页发起会话**

```bash
curl -X POST http://localhost:8080/api/chat/conversations \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"merchantId":2,"productId":1677}'
```

**商家回复**

```bash
curl -X POST http://localhost:8080/api/merchant/chat/conversations/1/messages \
  -H "Authorization: Bearer <merchant-token>" \
  -H "Content-Type: application/json" \
  -d '{"type":"TEXT","text":"在的"}'
```

**与买小购对话（SSE，-N 关闭缓冲）**

```bash
curl -N -X POST http://localhost:8080/api/agent/converse \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"message":"hello, what can you do?"}'
```
