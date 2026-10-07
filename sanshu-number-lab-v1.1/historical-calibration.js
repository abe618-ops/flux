(function (root) {
  "use strict";

  const CONFIG = Object.freeze({
    sourcePeriods: 100,
    baselineCount: 2.01375,
    minN: 10,
    shrinkK: 50,
    scale: 4.0,
    cap: 0.80,
    singles: Object.freeze([
      { key: "earth-ding", label: "地盘干=丁", lift: 1.15, n: 90 },
      { key: "god-zhifu", label: "八神=值符", lift: 1.12, n: 100 },
      { key: "door-jing", label: "八门=景门", lift: 1.14, n: 100 },
      { key: "god-jiutian", label: "八神=九天", lift: 1.07, n: 100 },
      { key: "tstem-ji", label: "天盘干=己", lift: 1.06, n: 90 },
      { key: "tstem-xin", label: "天盘干=辛", lift: 0.88, n: 90 },
      { key: "void", label: "旬空宫", lift: 0.94, n: 180 },
      { key: "god-taiyin", label: "八神=太阴", lift: 0.92, n: 100 },
      { key: "god-xuanwu", label: "八神=玄武", lift: 0.87, n: 100 }
    ]),
    doubles: Object.freeze([
      { key: "ji-palace-generates-star", label: "天盘干=己＋宫生星", lift: 1.29, n: 15 },
      { key: "ding-horse", label: "地盘干=丁＋驿马", lift: 1.39, n: 15 }
    ])
  });

  const STAR_ELEMENT = Object.freeze({
    天蓬: "水", 天任: "土", 天冲: "木", 天辅: "木",
    天英: "火", 天芮: "土", 天柱: "金", 天心: "金", 天禽: "土"
  });
  const GENERATES = Object.freeze({ 木: "火", 火: "土", 土: "金", 金: "水", 水: "木" });

  function contribution(lift, n) {
    if (!Number.isFinite(n) || n < CONFIG.minN) return 0;
    return (lift - 1) * (n / (n + CONFIG.shrinkK)) * CONFIG.scale;
  }

  function isVoidPalace(qimen, gong) {
    return (qimen.voidPalaces || []).some(function (item) {
      return Number(item.palace) === Number(gong);
    });
  }

  function isHorsePalace(qimen, gong) {
    return !!(qimen.horseStar && Number(qimen.horseStar.palace) === Number(gong));
  }

  function palaceGeneratesStar(palace) {
    const star = palace && palace.tianPan && palace.tianPan.star;
    return !!(palace && palace.element && star && GENERATES[palace.element] === STAR_ELEMENT[star]);
  }

  function calibratePalace(qimen, palace) {
    if (!palace || Number(palace.gong) === 5) {
      return { delta: 0, matches: [], excluded: "中五宫未纳入外八宫历史样本" };
    }

    const matches = [];
    function use(rule, hit) {
      if (!hit || rule.n < CONFIG.minN) return;
      matches.push({
        key: rule.key,
        label: rule.label,
        lift: rule.lift,
        n: rule.n,
        contribution: contribution(rule.lift, rule.n)
      });
    }

    const tStem = palace.tianPan && palace.tianPan.stem;
    const earth = palace.diPan && palace.diPan.stem;
    const god = palace.shenPan && palace.shenPan.god;
    const door = palace.renPan && palace.renPan.door;
    const s = CONFIG.singles;
    const d = CONFIG.doubles;

    use(s[0], earth === "丁");
    use(s[1], god === "值符"); // 与“值符临宫=1”同源，不重复计权
    use(s[2], door === "景门");
    use(s[3], god === "九天");
    use(s[4], tStem === "己");
    use(s[5], tStem === "辛");
    use(s[6], isVoidPalace(qimen, palace.gong));
    use(s[7], god === "太阴");
    use(s[8], god === "玄武");

    use(d[0], tStem === "己" && palaceGeneratesStar(palace));
    use(d[1], earth === "丁" && isHorsePalace(qimen, palace.gong));

    const rawDelta = matches.reduce(function (sum, item) {
      return sum + item.contribution;
    }, 0);
    const delta = Math.max(-CONFIG.cap, Math.min(CONFIG.cap, rawDelta));
    return { delta: delta, rawDelta: rawDelta, matches: matches, excluded: "" };
  }

  function calibrateQimen(qimen) {
    return (qimen.jiuGongGe || []).map(function (palace) {
      const result = calibratePalace(qimen, palace);
      return {
        gong: palace.gong,
        name: palace.name,
        delta: result.delta,
        rawDelta: result.rawDelta || 0,
        matches: result.matches,
        excluded: result.excluded
      };
    });
  }

  root.SanshuHistoricalCalibration = Object.freeze({
    CONFIG: CONFIG,
    calibratePalace: calibratePalace,
    calibrateQimen: calibrateQimen
  });
})(typeof globalThis !== "undefined" ? globalThis : window);
