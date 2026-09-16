# v1.2 自动同向规则与界面压缩

## 前两项自动重抽

每次新建盲测时：

1. 用已结算样本计算 15 家方法的当前综合分。
2. 综合分从高到低排序，取第 1、2 名作为 `top2`。
3. 生成整套随机起盘。
4. 分别读取 `top2` 的主胜/客胜方向。
5. 两者不同则重新生成整套随机起盘；两者相同才继续。
6. 同向后再计算总体合参和 2–5 家共振，并冻结赛前结果。
7. `topPairGate` 写入记录：`families / names / directions / side / attempts / aligned / rule`。

伪代码：

```js
const top2 = rankByHistoricalComposite(records).slice(0, 2);
let attempts = 0;
do {
  attempts++;
  forecast = randomForecast();
  directions = top2.map(f => sidePick(forecast[f].result));
} while (directions[0] !== directions[1] && attempts < 240);
```

正常情况下只需少量重抽；240 次上限只是避免极端设备异常造成卡死。

## 紧凑界面

随机合参页改成：

`场次输入 → 开始 → 紧凑绿色结果 → 15家准确率排序列表 → 总体建议/共振折叠 → 赛果复盘`

删除或折叠原来顶部重复的英文标题、长说明、规则说明和封存文字；15 家纵向列表直接提前到结果下面。前两项使用浅色背景突出，并显示“自动抽取 N 次后同向”。

## 并存安装

v1.1：`com.abe618.footballfusion.lab`

v1.2：`com.abe618.footballfusion.v12`

v1.2 同时使用独立签名，因此两版可在 Android 上同时保留。