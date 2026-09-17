// Standalone reference for the result-layer logic added to 三术数研.
// The original traditional-engine.js and number-core.js are intentionally unchanged.

export const DIRECTION_RULES = Object.freeze({
  positionThreshold: 5,
  methodThreshold: 5,
  maxAttempts: 500,
});

const sumDigits = (values = []) => values.reduce((total, value) => total + Number(value || 0), 0);

export function analyzeDirection(reading) {
  const hundred = (reading.positions || []).find((item) => item.label === "百位");
  const unit = (reading.positions || []).find((item) => item.label === "个位");

  const hundredSum = sumDigits(hundred?.digits);
  const unitSum = sumDigits(unit?.digits);
  const qimenSum = sumDigits(reading.methods?.qimen?.topDigits);
  const liuyaoSum = sumDigits(reading.methods?.liuyao?.topDigits);

  const dimensionOneDiff = hundredSum - unitSum;
  const dimensionTwoDiff = qimenSum - liuyaoSum;
  const oneStrong = Math.abs(dimensionOneDiff) >= DIRECTION_RULES.positionThreshold;
  const twoStrong = Math.abs(dimensionTwoDiff) >= DIRECTION_RULES.methodThreshold;
  const sameDirection =
    dimensionOneDiff !== 0 &&
    dimensionTwoDiff !== 0 &&
    Math.sign(dimensionOneDiff) === Math.sign(dimensionTwoDiff);

  const triggered = oneStrong && twoStrong && sameDirection;
  return {
    hundredSum,
    unitSum,
    qimenSum,
    liuyaoSum,
    dimensionOneDiff,
    dimensionTwoDiff,
    triggered,
    direction: triggered ? (dimensionOneDiff > 0 ? "上盘" : "下盘") : "未触发",
  };
}

export function footballEstimate(signal) {
  if (!signal?.triggered) {
    return { direction: "未触发", handicap: "—", goals: "—", score: "—" };
  }

  const strength = Math.abs(signal.dimensionOneDiff) + Math.abs(signal.dimensionTwoDiff);
  let tier = 0;
  if (strength >= 26) tier = 3;
  else if (strength >= 20) tier = 2;
  else if (strength >= 14) tier = 1;

  const upper = signal.direction === "上盘";
  const handicapUpper = ["-0.25~-0.5", "-0.5~-0.75", "-0.75~-1.0", "-1.0~-1.25"];
  const handicapLower = ["+0.25~+0.5", "+0.5~+0.75", "+0.75~+1.0", "+1.0~+1.25"];
  const goals = ["2~3球", "2~3球", "3~4球", "3~4球"][tier];
  const scoreUpper = ["1:0 / 2:1", "2:1 / 1:0", "2:0 / 3:1", "3:0 / 3:1"];
  const scoreLower = ["1:1 / 1:2", "1:2 / 0:1", "0:2 / 1:3", "0:3 / 1:3"];

  return {
    direction: signal.direction,
    handicap: upper ? `上盘 ${handicapUpper[tier]}` : `下盘 ${handicapLower[tier]}`,
    goals,
    score: upper ? scoreUpper[tier] : scoreLower[tier],
  };
}
