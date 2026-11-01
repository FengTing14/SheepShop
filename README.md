# SheepShop

地牢币商店插件。价格扣 **SheepDungeon 的地牢币**，与主世界货币（XConomy）无关。

## 两种商店

### 房间商店（自动）

玩家走进 SheepDungeon 的 `SHOP` 房间后自动打开界面。

- 商品来自那个房间的配置：`plugins/SheepDungeon/dungeon/<地牢id>/room/shop_room.yml`
- 本插件只控制外观与开关（`room-shop-enabled`、`room-shop-open-delay`、`room-shop-title`）
- 好处：商品跟着地牢走，不同地牢可以卖不同的东西

```yaml
# plugins/SheepDungeon/dungeon/sample/room/shop_room.yml
shop_room:
  display: "商店房"
  type: SHOP
  weight: 15
  shop:
    products:
      - item-id: "sheeplin_material:magic_shard"   # CraftEngine 物品写 命名空间:id
        price: 12
      - item-id: "GOLDEN_APPLE"                    # 原版材质直接写材质名
        price: 8
```

### 常驻商店（手动）

`/shop open <商店id>` 打开，商品写在 `plugins/SheepShop/config.yml` 的 `shops` 段：

```yaml
shops:
  supply:
    title: "§8补给站 §7· 地牢币 §e%coins%"
    products:
      - item-id: "GOLDEN_APPLE"
        amount: 1
        price: 8
        display: "§6金苹果"
```

## 命令

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/shop` | `sheepshop.use` | 打开默认常驻商店 |
| `/shop open <id>` | `sheepshop.use` | 打开指定常驻商店 |
| `/shop open room` | `sheepshop.use` | 打开当前房间商店（不在商店房会提示） |
| `/shop coins [玩家]` | `sheepshop.use` | 查看地牢币余额 |
| `/shop list` | `sheepshop.use` | 列出常驻商店 |
| `/shop reload` | `sheepshop.admin` | 重载配置 |

## 配置

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `room-shop-enabled` | `true` | 是否启用房间商店 |
| `room-shop-open-delay` | `10` | 进商店房后延迟多少 tick 打开（20 tick = 1 秒） |
| `room-shop-title` | `§8商店 §7· 地牢币 §e%coins%` | 房间商店标题，`%coins%` 是余额 |
| `rows` | `3` | 界面行数（1-6），商品超出一页会告警并截断 |
| `messages.*` | | 各类提示文本 |

## 依赖

- **必需**：`SheepDungeon`（提供 `SheepDungeonAPI`：地牢币读写、房间查询、按 ID 造物品）
- **可选**：`CraftEngine`（卖 CraftEngine 自定义物品时需要；只卖原版材质可以不装）

## 构建

```bash
# 先构建 SheepDungeon，SheepShop 编译时要用它的 API
cd ../SheepDungeon && ./gradlew build -x test

cd ../SheepShop && ./gradlew build -x test
# 产物：build/libs/SheepShop-1.0.0-SNAPSHOT.jar
```

要求 JDK 25 + Gradle 9.2.1（wrapper 已带）。

## 实现要点

- **界面归属用 `InventoryHolder` 标记**，不靠标题字符串比对。标题里带余额，玩家买完东西标题会变，按标题认界面会失效。
- **点击一律取消**，只对「左键点商店格子」结算，禁止把物品拖进商店或拿出去。
- **扣币和发货的顺序**：先 `takeCoins` 成功再发货。`takeCoins` 内部是「余额不足就不扣」的原子判断，避免先发货后扣币扣失败。
- 背包满了物品掉在脚下，并提示玩家，不会吞掉。

## 相关

- 地牢币账本在 SheepDungeon 的 `coins.yml`
- 兑换比例：2 地牢币 = 1 主世界币
