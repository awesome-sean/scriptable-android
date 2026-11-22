const BOOKING_URL = "https://mobilereserve.cqu.edu.cn/#/login";
const API_URL = "https://uapis.cn/api/v1/misc/weather?adcode=500106&forecast=true&lang=zh";

function targetDate(now) {
  // 使用 UTC 运算计算北京时间的后天，不受手机所在时区影响。
  const cq = new Date(now.getTime() + 8 * 3600000);
  return new Date(Date.UTC(cq.getUTCFullYear(), cq.getUTCMonth(), cq.getUTCDate() + 2))
    .toISOString().slice(0, 10);
}

function inspectForecast(data, now) {
  if (!data || typeof data !== "object" || data.error || !Array.isArray(data.forecast)) {
    throw new Error("天气接口返回错误或缺少每日预报");
  }
  if (String(data.adcode) !== "500106") {
    throw new Error("天气预报地区不匹配，需为重庆市沙坪坝区（500106）");
  }
  const day = targetDate(now);
  const matches = data.forecast.filter(item => item && item.date === day);
  if (matches.length !== 1) throw new Error("预约日期的预报缺失或重复：" + day);
  const daily = matches[0];
  if (typeof daily.weather_day !== "string" || !daily.weather_day.trim()) {
    throw new Error("预约日期缺少白天天气");
  }
  const weather = daily.weather_day.trim();
  const night = typeof daily.weather_night === "string" ? daily.weather_night.trim() : "";
  // 温度仅用于展示，缺失时明确标注，不影响晴天判断。
  const validTemp = value => typeof value === "number" && Number.isFinite(value);
  const high = daily.temp_max;
  const low = daily.temp_min;
  const temperature = validTemp(high) && validTemp(low) && low <= high
    ? low + "～" + high + "℃" : "温度数据暂缺";
  // 严格匹配「晴」，避免将「晴转雨」「多云转晴」等误判为全天白天晴。
  return { day, weather, night, temperature, eligible: weather === "晴" };
}

async function fetchForecast() {
  const request = new Request(API_URL);
  request.timeoutInterval = config.runsInWidget ? 12 : 20;
  const data = await request.loadJSON();
  const status = request.response && request.response.statusCode;
  if (status && (status < 200 || status >= 300)) {
    throw new Error("天气请求失败：HTTP " + status);
  }
  const checkedAt = new Date();
  return { result: inspectForecast(data, checkedAt), checkedAt };
}

// 小组件中的日期和检查时间统一使用北京时间。
function beijingStamp(date) {
  return new Date(date.getTime() + 8 * 3600000).toISOString().slice(5, 16).replace("T", " ");
}

function buildWidget(result, checkedAt, error) {
  const widget = new ListWidget();
  widget.setPadding(14, 14, 14, 14);
  const positive = result && result.eligible && !error;
  const background = error ? "424D60" : positive ? "265A50" : "304B70";
  const accent = error ? "FFD290" : positive ? "B9F3D6" : "CDDEFF";
  widget.backgroundColor = new Color(background);
  function text(parent, value, size, color, bold) {
    const line = parent.addText(value);
    line.font = bold ? Font.semiboldSystemFont(size) : Font.systemFont(size);
    line.textColor = new Color(color || "FFFFFF");
    line.lineLimit = 1;
    line.minimumScaleFactor = 0.75;
    return line;
  }
  // 四行：地点与目标日期 / 大号天气与图标 / 温度 / 建议与检查时间。
  const day = result ? result.day : targetDate(checkedAt);
  const header = widget.addStack();
  header.centerAlignContent();
  text(header, "重庆大学", 11, "D5DEEB");
  header.addSpacer();
  text(header, day.slice(5).replace("-", "/"), 12, "D5DEEB");
  widget.addSpacer();
  const weatherRow = widget.addStack();
  weatherRow.centerAlignContent();
  const weatherText = text(weatherRow, error ? "未更新" : result.weather,
    error ? 25 : 30, "FFFFFF", true);
  weatherText.minimumScaleFactor = 0.55;
  weatherRow.addSpacer();
  const weather = result ? result.weather : "";
  const symbolName = error ? "exclamationmark.triangle"
    : weather === "晴" ? "sun.max.fill"
    : weather.includes("雪") ? "cloud.snow.fill"
    : weather.includes("雨") ? "cloud.rain.fill"
    : weather.includes("多云") ? "cloud.sun.fill" : "cloud.fill";
  // 图标只辅助识别；保留文字，系统缺少对应图标时仍能显示。
  try {
    const symbol = SFSymbol.named(symbolName);
    if (symbol) {
      const image = weatherRow.addImage(symbol.image);
      image.imageSize = new Size(28, 28);
      image.tintColor = new Color(positive ? "FFD483" : accent);
    }
  } catch (_) {}
  widget.addSpacer(2);
  text(widget, error ? "点按重试" : result.temperature, 13, "D5DEEB");
  widget.addSpacer();
  const footer = widget.addStack();
  footer.centerAlignContent();
  text(footer, error ? "待检查" : positive ? "建议预约" : "暂不提醒", 12, accent, true);
  footer.addSpacer(6);
  footer.addSpacer();
  text(footer, beijingStamp(checkedAt).replace("-", "/"), 9, "B7C4D6");
  // Small 只有整个组件一个点击区域。失败时打开本脚本，正常时打开预约入口。
  widget.url = error
    ? URLScheme.forRunningScript() + "&shortcutParameter=preview"
    : BOOKING_URL;
  // 申请约 30 分钟后更新，临近北京时间零点时更早申请；实际由 iOS 调度。
  const cq = new Date(checkedAt.getTime() + 8 * 3600000);
  const midnight = Date.UTC(cq.getUTCFullYear(), cq.getUTCMonth(), cq.getUTCDate() + 1) - 8 * 3600000;
  widget.refreshAfterDate = new Date(Math.min(checkedAt.getTime() + 30 * 60000, midnight + 1000));
  return widget;
}

async function run() {
  try {
    const { result } = await fetchForecast();
    const summary = result.day + "；白天：" + result.weather
      + "；夜间：" + (result.night || "数据暂缺") + "；" + result.temperature
      + "。预报地区：重庆市沙坪坝区（UAPI）。";
    if (result.eligible) {
      const notification = new Notification();
      notification.title = "建议预约 " + result.day;
      notification.subtitle = "重庆大学科学城校区";
      notification.body = summary + "\n白天预报为晴，建议现在预约。\n" + BOOKING_URL;
      notification.sound = "default";
      notification.openURL = BOOKING_URL;
      notification.threadIdentifier = "cqu-weather-reservation";
      // 每次检查达标都提醒，不设每日去重，也不覆盖上一条通知。
      await notification.schedule();
    }
    const message = (result.eligible ? "已提交预约提醒。" : "后天白天预报不是晴，本次不发送预约通知。") + summary;
    console.log(message);
    Script.setShortcutOutput(message);
  } catch (error) {
    // 请求、解析、通知出错时向快捷指令返回原因，不以缓存或示例数据代替预报。
    const message = "天气检查或提醒失败：" + String(error.message || error);
    console.error(message);
    Script.setShortcutOutput(message);
  }
}

async function main() {
  const query = args.queryParameters || {};
  const defaultMode = config.runsInApp ? "preview" : "notify";
  const parameter = String(args.shortcutParameter || query.mode || defaultMode).trim().toLowerCase();
  const preview = !config.runsInWidget && parameter === "preview";
  if (!config.runsInWidget && parameter !== "notify" && parameter !== "preview") {
    const message = "参数无效：请使用 notify 检查天气，或 preview 预览小组件。";
    console.error(message);
    Script.setShortcutOutput(message);
    return;
  }
  if (config.runsInWidget || preview) {
    let result = null;
    let checkedAt = new Date();
    let error = null;
    try {
      const forecast = await fetchForecast();
      result = forecast.result;
      checkedAt = forecast.checkedAt;
    } catch (failure) {
      error = failure;
      console.error("小组件天气检查失败：" + String(failure.message || failure));
    }
    const widget = buildWidget(result, checkedAt, error);
    Script.setWidget(widget);
    if (preview) {
      await widget.presentSmall();
      console.log("小组件已生成，可在预览下方添加到主屏幕。选择脚本“平安重大”，尺寸选“小”，组件参数留空。");
      console.log("预览和桌面刷新不会发送通知；定时检查请使用 notify 参数。");
    }
  } else {
    await run();
  }
}

await main();
Script.complete();
